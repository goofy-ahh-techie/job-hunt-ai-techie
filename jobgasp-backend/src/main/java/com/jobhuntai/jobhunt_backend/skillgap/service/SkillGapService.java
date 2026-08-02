package com.jobhuntai.jobhunt_backend.skillgap.service;

import com.jobhuntai.jobhunt_backend.common.exception.GapAnalysisFailedException;
import com.jobhuntai.jobhunt_backend.common.exception.IntelligenceServiceUnavailableException;
import com.jobhuntai.jobhunt_backend.common.exception.JdIntelligenceNotFoundException;
import com.jobhuntai.jobhunt_backend.common.exception.ResourceNotFoundException;
import com.jobhuntai.jobhunt_backend.common.exception.ResumeVersionNotFoundException;
import com.jobhuntai.jobhunt_backend.common.exception.SkillGapNotFoundException;
import com.jobhuntai.jobhunt_backend.jd.domain.JdIntelligence;
import com.jobhuntai.jobhunt_backend.jd.repository.JdIntelligenceRepository;
import com.jobhuntai.jobhunt_backend.matching.domain.MatchResult;
import com.jobhuntai.jobhunt_backend.matching.repository.MatchResultRepository;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeChunk;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeVersion;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeChunkRepository;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeRepository;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeVersionRepository;
import com.jobhuntai.jobhunt_backend.skillgap.client.GapAnalysisClientRequest;
import com.jobhuntai.jobhunt_backend.skillgap.client.GapAnalysisClientResult;
import com.jobhuntai.jobhunt_backend.skillgap.client.GapItemResult;
import com.jobhuntai.jobhunt_backend.skillgap.client.SkillGapIntelligenceClient;
import com.jobhuntai.jobhunt_backend.skillgap.client.StandaloneGapClientResult;
import com.jobhuntai.jobhunt_backend.skillgap.domain.GapItem;
import com.jobhuntai.jobhunt_backend.skillgap.domain.GapPriority;
import com.jobhuntai.jobhunt_backend.skillgap.domain.SkillGap;
import com.jobhuntai.jobhunt_backend.skillgap.domain.SkillGapStatus;
import com.jobhuntai.jobhunt_backend.skillgap.dto.SkillGapResponse;
import com.jobhuntai.jobhunt_backend.skillgap.dto.SkillGapSummaryResponse;
import com.jobhuntai.jobhunt_backend.skillgap.dto.StandaloneGapResponse;
import com.jobhuntai.jobhunt_backend.skillgap.extractor.ExtractedGaps;
import com.jobhuntai.jobhunt_backend.skillgap.extractor.GapExtractor;
import com.jobhuntai.jobhunt_backend.skillgap.mapper.SkillGapMapper;
import com.jobhuntai.jobhunt_backend.skillgap.repository.SkillGapRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates skill gap analysis: read what the match already found, ask the LLM why
 * it matters and how to close it, and upsert the result.
 *
 * <p>Not {@code @Transactional} at the class level, for the same reason as every
 * service since Phase 2: the LLM call is a slow remote request that must not hold a DB
 * transaction open, and the FAILED-recording write has to survive the exception that
 * triggered it. Atomic writes go through {@link SkillGapPersistenceService}; reads are
 * read-only transactions.
 *
 * <p>Every read is ownership-scoped, so a caller cannot analyse — or confirm the
 * existence of — another user's match, resume, or JD.
 */
@Service
@RequiredArgsConstructor
public class SkillGapService {

    private static final Logger log = LoggerFactory.getLogger(SkillGapService.class);

    /** Resume context sent with a match-tied analysis. */
    static final int MATCH_RESUME_SUMMARY_LIMIT = 3000;

    /** Resume context sent with a standalone analysis — the whole resume is the input. */
    static final int STANDALONE_RESUME_LIMIT = 4000;

    static final String NO_GAPS_SUMMARY =
            "No significant gaps identified. Resume is a strong match for this role.";

    private final MatchResultRepository matchResultRepository;
    private final JdIntelligenceRepository jdIntelligenceRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeVersionRepository resumeVersionRepository;
    private final ResumeChunkRepository resumeChunkRepository;
    private final SkillGapRepository skillGapRepository;
    private final SkillGapPersistenceService persistenceService;
    private final SkillGapIntelligenceClient intelligenceClient;
    private final GapExtractor gapExtractor;

    /**
     * Analyse the gaps behind one match, upserting the result.
     *
     * <p>The row is written twice on purpose: as {@code ANALYZING} before the LLM call
     * (which takes tens of seconds), then again with the finished analysis. A
     * re-analysis reuses the existing row, which is what keeps {@code lastAnalyzedAt}
     * meaningful.
     */
    public SkillGapResponse analyzeMatchGap(UUID userId, UUID matchId) {
        MatchResult matchResult = requireOwnedMatch(matchId, userId);

        JdIntelligence jdIntelligence = jdIntelligenceRepository
                .findByJobDescriptionId(matchResult.getJobDescriptionId())
                .orElseThrow(() -> new JdIntelligenceNotFoundException(
                        "Job description has not been extracted yet: "
                                + matchResult.getJobDescriptionId()));

        String resumeSummary = loadResumeText(matchResult.getResumeId(), MATCH_RESUME_SUMMARY_LIMIT);

        SkillGap skillGap = skillGapRepository.findByMatchResultId(matchId)
                .orElseGet(() -> newSkillGap(userId, matchResult));
        skillGap.setStatus(SkillGapStatus.ANALYZING);
        skillGap.setOverallScoreContext(matchResult.getOverallScore());
        skillGap = persistenceService.save(skillGap);

        ExtractedGaps gaps = gapExtractor.extract(matchResult, jdIntelligence);

        if (gaps.isEmpty()) {
            // Nothing to explain. Calling the LLM here would invite it to invent a
            // gap to justify the request — the one failure mode a gap analysis must
            // never have.
            log.debug("Match {} has no gaps; skipping LLM call", matchId);
            return completeWithNoGaps(skillGap);
        }

        GapAnalysisClientRequest request = new GapAnalysisClientRequest(
                jdIntelligence.getJobTitle(),
                jdIntelligence.getCompanyName(),
                gaps.missingSkills(),
                gaps.missingMustHaves(),
                gaps.preferredMissing(),
                resumeSummary,
                matchResult.getOverallScore() == null
                        ? 0.0 : matchResult.getOverallScore().doubleValue());

        try {
            GapAnalysisClientResult result = intelligenceClient.analyzeGap(request);
            applyResult(skillGap, result);
            skillGap.setStatus(SkillGapStatus.COMPLETED);
            skillGap.setAnalysisError(null);
            skillGap.setLastAnalyzedAt(Instant.now());
            skillGap = persistenceService.save(skillGap);

            log.debug("Gap analysis complete for match {}: {} gap(s)", matchId, gaps.total());
            return SkillGapMapper.toSkillGapResponse(skillGap);
        } catch (IntelligenceServiceUnavailableException ex) {
            // State compensation, not HTTP mapping: the row must not be left parked in
            // ANALYZING. Status mapping stays in the global handler.
            markFailed(skillGap, "Intelligence service unavailable");
            throw ex;
        } catch (GapAnalysisFailedException ex) {
            markFailed(skillGap, ex.getMessage());
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public SkillGapResponse getSkillGap(UUID skillGapId, UUID userId) {
        SkillGap gap = skillGapRepository.findByIdAndUserId(skillGapId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Skill gap analysis not found: " + skillGapId));
        return SkillGapMapper.toSkillGapResponse(gap);
    }

    @Transactional(readOnly = true)
    public SkillGapResponse getSkillGapByMatch(UUID matchId, UUID userId) {
        // Ownership is checked on the match first: without it, "no analysis yet" and
        // "not your match" would be indistinguishable to a probing caller.
        requireOwnedMatch(matchId, userId);
        SkillGap gap = skillGapRepository.findByMatchResultId(matchId)
                .orElseThrow(() -> new SkillGapNotFoundException(
                        "No gap analysis has been run for match: " + matchId));
        return SkillGapMapper.toSkillGapResponse(gap);
    }

    @Transactional(readOnly = true)
    public List<SkillGapSummaryResponse> getSkillGapsForResume(UUID resumeId, UUID userId) {
        requireOwnedResume(resumeId, userId);
        return SkillGapMapper.toSkillGapSummaryResponseList(
                skillGapRepository.findAllByResumeIdAndUserIdOrderByLastAnalyzedAtDesc(
                        resumeId, userId));
    }

    @Transactional(readOnly = true)
    public List<SkillGapSummaryResponse> getSkillGapsForJd(UUID jdId, UUID userId) {
        return SkillGapMapper.toSkillGapSummaryResponseList(
                skillGapRepository.findAllByJobDescriptionIdAndUserIdOrderByLastAnalyzedAtDesc(
                        jdId, userId));
    }

    /**
     * Assess a resume with no target role. Deliberately not persisted: it derives from
     * nothing but the resume, so a stored copy would silently go stale the moment the
     * resume changed, and recomputing costs one LLM call.
     */
    public StandaloneGapResponse analyzeResumeStandalone(UUID userId, UUID resumeId) {
        requireOwnedResume(resumeId, userId);
        String resumeText = loadResumeText(resumeId, STANDALONE_RESUME_LIMIT);

        StandaloneGapClientResult result = intelligenceClient.analyzeStandalone(resumeText);
        return SkillGapMapper.toStandaloneGapResponse(resumeId, result);
    }

    // --- helpers ---

    private SkillGap newSkillGap(UUID userId, MatchResult matchResult) {
        return SkillGap.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .matchResultId(matchResult.getId())
                .resumeId(matchResult.getResumeId())
                .jobDescriptionId(matchResult.getJobDescriptionId())
                .overallScoreContext(matchResult.getOverallScore())
                .status(SkillGapStatus.PENDING)
                .build();
    }

    /** The perfect-match path: a positive summary and empty lists, no LLM call. */
    private SkillGapResponse completeWithNoGaps(SkillGap skillGap) {
        skillGap.setGapSummary(NO_GAPS_SUMMARY);
        skillGap.setGaps(SkillGapMapper.serializeGaps(List.of()));
        skillGap.setQuickWins(SkillGapMapper.serializeList(List.of()));
        skillGap.setDealBreakers(SkillGapMapper.serializeList(List.of()));
        skillGap.setStatus(SkillGapStatus.COMPLETED);
        skillGap.setAnalysisError(null);
        skillGap.setLastAnalyzedAt(Instant.now());
        return SkillGapMapper.toSkillGapResponse(persistenceService.save(skillGap));
    }

    private void applyResult(SkillGap skillGap, GapAnalysisClientResult result) {
        skillGap.setGapSummary(result.gapSummary());
        skillGap.setGaps(SkillGapMapper.serializeGaps(toGapItems(result.gaps())));
        skillGap.setQuickWins(SkillGapMapper.serializeList(result.quickWins()));
        skillGap.setDealBreakers(SkillGapMapper.serializeList(result.dealBreakers()));
    }

    private List<GapItem> toGapItems(List<GapItemResult> results) {
        return results.stream()
                .map(item -> new GapItem(
                        item.skill(),
                        GapPriority.fromString(item.priority()),
                        item.reason(),
                        item.learningRecommendation(),
                        item.estimatedWeeks()))
                .toList();
    }

    /**
     * Record the failure without letting a second failure mask the first: if the
     * compensating write itself fails there is nothing useful left to do, and throwing
     * from here would replace the real cause with a persistence error.
     */
    private void markFailed(SkillGap skillGap, String error) {
        try {
            skillGap.setStatus(SkillGapStatus.FAILED);
            skillGap.setAnalysisError(error);
            persistenceService.save(skillGap);
        } catch (Exception persistenceFailure) {
            log.error("Could not record FAILED status for skill gap {}", skillGap.getId(),
                    persistenceFailure);
        }
    }

    /**
     * The latest resume version's chunk text, concatenated and truncated.
     *
     * <p>Truncation is a prompt-budget decision: the model's context is finite, and the
     * first few thousand characters of a resume carry the summary, current role, and
     * skills — the parts a gap recommendation needs to route learning through existing
     * experience.
     */
    private String loadResumeText(UUID resumeId, int limit) {
        ResumeVersion version = resumeVersionRepository
                .findTopByResumeIdOrderByVersionNumberDesc(resumeId)
                .orElseThrow(() -> new ResumeVersionNotFoundException(
                        "Resume has no parsed version to analyse: " + resumeId));

        String text = resumeChunkRepository
                .findAllByResumeVersionIdOrderByChunkIndex(version.getId()).stream()
                .map(ResumeChunk::getContent)
                .filter(content -> content != null && !content.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private MatchResult requireOwnedMatch(UUID matchId, UUID userId) {
        return matchResultRepository.findByIdAndUserId(matchId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + matchId));
    }

    private void requireOwnedResume(UUID resumeId, UUID userId) {
        resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found: " + resumeId));
    }
}
