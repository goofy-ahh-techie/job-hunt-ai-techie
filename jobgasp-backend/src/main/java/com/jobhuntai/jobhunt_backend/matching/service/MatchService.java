package com.jobhuntai.jobhunt_backend.matching.service;

import com.jobhuntai.jobhunt_backend.common.exception.JdIntelligenceNotFoundException;
import com.jobhuntai.jobhunt_backend.common.exception.MatchCalculationFailedException;
import com.jobhuntai.jobhunt_backend.common.exception.ResourceNotFoundException;
import com.jobhuntai.jobhunt_backend.common.exception.ResumeVersionNotFoundException;
import com.jobhuntai.jobhunt_backend.jd.domain.JdIntelligence;
import com.jobhuntai.jobhunt_backend.jd.mapper.JdMapper;
import com.jobhuntai.jobhunt_backend.jd.repository.JdIntelligenceRepository;
import com.jobhuntai.jobhunt_backend.jd.repository.JobDescriptionRepository;
import com.jobhuntai.jobhunt_backend.matching.domain.MatchResult;
import com.jobhuntai.jobhunt_backend.matching.domain.MatchStatus;
import com.jobhuntai.jobhunt_backend.matching.dto.MatchRequest;
import com.jobhuntai.jobhunt_backend.matching.dto.MatchResponse;
import com.jobhuntai.jobhunt_backend.matching.dto.MatchSummaryResponse;
import com.jobhuntai.jobhunt_backend.matching.mapper.MatchMapper;
import com.jobhuntai.jobhunt_backend.matching.repository.MatchResultRepository;
import com.jobhuntai.jobhunt_backend.matching.scoring.OrchestratedScores;
import com.jobhuntai.jobhunt_backend.matching.scoring.ScoreExplanationBuilder;
import com.jobhuntai.jobhunt_backend.matching.scoring.ScoringContext;
import com.jobhuntai.jobhunt_backend.matching.scoring.SubScoreOrchestrator;
import com.jobhuntai.jobhunt_backend.matching.scoring.WeightedScoreCalculator;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeChunk;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeVersion;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeChunkRepository;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeRepository;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeVersionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates match calculation: load both sides, score six dimensions, weight them,
 * and upsert the result.
 *
 * <p>Not {@code @Transactional} at the class level, for the same reason as
 * {@code JdService} and {@code ResumeService}: scoring makes slow remote calls that
 * must not hold a DB transaction open, and the FAILED-recording write has to survive
 * the exception that triggered it. Atomic writes go through
 * {@link MatchPersistenceService}; reads are read-only transactions.
 *
 * <p>Every read is ownership-scoped through {@code findByIdAndUserId}, so a caller
 * cannot score, or even confirm the existence of, another user's resume or JD.
 */
@Service
@RequiredArgsConstructor
public class MatchService {

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);

    private final ResumeRepository resumeRepository;
    private final ResumeVersionRepository resumeVersionRepository;
    private final ResumeChunkRepository resumeChunkRepository;
    private final JobDescriptionRepository jobDescriptionRepository;
    private final JdIntelligenceRepository jdIntelligenceRepository;
    private final MatchResultRepository matchResultRepository;
    private final MatchPersistenceService persistenceService;
    private final SubScoreOrchestrator orchestrator;
    private final WeightedScoreCalculator scoreCalculator;
    private final ScoreExplanationBuilder explanationBuilder;

    /**
     * Score one resume against one job description, upserting the result.
     *
     * <p>The row is written twice on purpose. It is saved as {@code CALCULATING}
     * before any scoring starts, so a run interrupted mid-flight (or one still in
     * progress — scoring can take tens of seconds) is distinguishable from one that
     * never began; then it is saved again with the finished scores. Recalculation
     * reuses the existing row rather than appending, which is what keeps
     * {@code lastCalculatedAt} meaningful.
     */
    public MatchResponse calculateMatch(UUID userId, MatchRequest request) {
        requireOwnedResume(request.resumeId(), userId);

        ResumeVersion version = resumeVersionRepository
                .findTopByResumeIdOrderByVersionNumberDesc(request.resumeId())
                .orElseThrow(() -> new ResumeVersionNotFoundException(
                        "Resume has no parsed version to match against: " + request.resumeId()));

        List<ResumeChunk> chunks =
                resumeChunkRepository.findAllByResumeVersionIdOrderByChunkIndex(version.getId());

        requireOwnedJd(request.jdId(), userId);

        JdIntelligence intelligence = jdIntelligenceRepository
                .findByJobDescriptionId(request.jdId())
                .orElseThrow(() -> new JdIntelligenceNotFoundException(
                        "Job description has not been extracted yet: " + request.jdId()));

        // Upsert: an existing row for this exact pair is a recalculation. The UNIQUE
        // constraint on (resume_version_id, job_description_id) is what makes this
        // lookup authoritative rather than a best guess.
        MatchResult result = matchResultRepository
                .findByResumeVersionIdAndJobDescriptionId(version.getId(), request.jdId())
                .orElseGet(() -> newMatchResult(userId, request, version.getId()));

        result.setStatus(MatchStatus.CALCULATING);
        result = persistenceService.save(result);

        try {
            ScoringContext context = new ScoringContext(version, chunks, intelligence);
            OrchestratedScores scores = orchestrator.orchestrate(context);
            BigDecimal overall = scoreCalculator.calculateExact(scores);

            applyScores(result, scores, overall);
            result.setScoreExplanation(explanationBuilder.build(scores, overall.doubleValue()));
            result.setStatus(MatchStatus.COMPLETED);
            result.setLastCalculatedAt(Instant.now());

            result = persistenceService.save(result);

            log.debug("Match calculated: resumeVersion={} jd={} overall={}",
                    version.getId(), request.jdId(), overall);
            return MatchMapper.toMatchResponse(result, scores);
        } catch (Exception ex) {
            // State compensation, not HTTP mapping: the row must not be left parked
            // in CALCULATING forever. Status mapping stays in the global handler.
            log.error("Match calculation failed for resume={} jd={}",
                    request.resumeId(), request.jdId(), ex);
            markFailed(result);
            throw new MatchCalculationFailedException(
                    "Match calculation failed: " + ex.getMessage(), ex);
        }
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatch(UUID matchId, UUID userId) {
        MatchResult result = matchResultRepository.findByIdAndUserId(matchId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + matchId));
        return MatchMapper.toMatchResponse(result);
    }

    @Transactional(readOnly = true)
    public List<MatchSummaryResponse> getMatchesForResume(UUID resumeId, UUID userId) {
        // Ownership is checked on the resume first: without it a caller could probe
        // for another user's resume id and read an empty list as "exists but unmatched".
        requireOwnedResume(resumeId, userId);
        return MatchMapper.toMatchSummaryResponseList(
                matchResultRepository.findAllByResumeIdAndUserIdOrderByOverallScoreDesc(
                        resumeId, userId));
    }

    @Transactional(readOnly = true)
    public List<MatchSummaryResponse> getMatchesForJd(UUID jdId, UUID userId) {
        requireOwnedJd(jdId, userId);
        return MatchMapper.toMatchSummaryResponseList(
                matchResultRepository.findAllByJobDescriptionIdAndUserIdOrderByOverallScoreDesc(
                        jdId, userId));
    }

    // --- helpers ---

    /**
     * A fresh row for a pair scored for the first time. The score columns are
     * {@code NOT NULL}, so they are zeroed here to survive the {@code CALCULATING}
     * insert that happens before any scoring has run.
     */
    private MatchResult newMatchResult(UUID userId, MatchRequest request, UUID versionId) {
        return MatchResult.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .resumeId(request.resumeId())
                .resumeVersionId(versionId)
                .jobDescriptionId(request.jdId())
                .overallScore(BigDecimal.ZERO)
                .mustHaveScore(BigDecimal.ZERO)
                .requiredSkillsScore(BigDecimal.ZERO)
                .responsibilitiesScore(BigDecimal.ZERO)
                .experienceScore(BigDecimal.ZERO)
                .qualificationsScore(BigDecimal.ZERO)
                .preferredSkillsScore(BigDecimal.ZERO)
                .status(MatchStatus.PENDING)
                .build();
    }

    private void applyScores(MatchResult result, OrchestratedScores scores, BigDecimal overall) {
        result.setOverallScore(overall);
        result.setMustHaveScore(WeightedScoreCalculator.round(scores.mustHave().rawScore()));
        result.setRequiredSkillsScore(WeightedScoreCalculator.round(scores.requiredSkills().rawScore()));
        result.setResponsibilitiesScore(WeightedScoreCalculator.round(scores.responsibilities().rawScore()));
        result.setExperienceScore(WeightedScoreCalculator.round(scores.experience().rawScore()));
        result.setQualificationsScore(WeightedScoreCalculator.round(scores.qualifications().rawScore()));
        result.setPreferredSkillsScore(WeightedScoreCalculator.round(scores.preferredSkills().rawScore()));

        // Only the keyword dimensions persist their evidence: those lists are the
        // actionable gaps a user works from. The rest live in the explanation text.
        result.setMustHaveMatched(JdMapper.serializeList(scores.mustHave().matched()));
        result.setMustHaveMissing(JdMapper.serializeList(scores.mustHave().missing()));
        result.setSkillsMatched(JdMapper.serializeList(scores.requiredSkills().matched()));
        result.setSkillsMissing(JdMapper.serializeList(scores.requiredSkills().missing()));
        result.setPreferredMatched(JdMapper.serializeList(scores.preferredSkills().matched()));
    }

    /**
     * Record the failure without letting a second failure mask the first: if the
     * compensating write itself fails there is nothing useful left to do, and throwing
     * from here would replace the real cause with a persistence error.
     */
    private void markFailed(MatchResult result) {
        try {
            result.setStatus(MatchStatus.FAILED);
            persistenceService.save(result);
        } catch (Exception persistenceFailure) {
            log.error("Could not record FAILED status for match {}", result.getId(),
                    persistenceFailure);
        }
    }

    private void requireOwnedResume(UUID resumeId, UUID userId) {
        resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found: " + resumeId));
    }

    private void requireOwnedJd(UUID jdId, UUID userId) {
        jobDescriptionRepository.findByIdAndUserId(jdId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Job description not found: " + jdId));
    }
}
