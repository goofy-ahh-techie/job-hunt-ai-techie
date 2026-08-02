package com.jobhuntai.jobhunt_backend.skillgap.service;

import com.jobhuntai.jobhunt_backend.common.exception.GapAnalysisFailedException;
import com.jobhuntai.jobhunt_backend.common.exception.IntelligenceServiceUnavailableException;
import com.jobhuntai.jobhunt_backend.common.exception.JdIntelligenceNotFoundException;
import com.jobhuntai.jobhunt_backend.common.exception.ResourceNotFoundException;
import com.jobhuntai.jobhunt_backend.common.exception.SkillGapNotFoundException;
import com.jobhuntai.jobhunt_backend.jd.domain.JdIntelligence;
import com.jobhuntai.jobhunt_backend.jd.mapper.JdMapper;
import com.jobhuntai.jobhunt_backend.jd.repository.JdIntelligenceRepository;
import com.jobhuntai.jobhunt_backend.matching.domain.MatchResult;
import com.jobhuntai.jobhunt_backend.matching.repository.MatchResultRepository;
import com.jobhuntai.jobhunt_backend.resume.domain.Resume;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeChunk;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeVersion;
import com.jobhuntai.jobhunt_backend.resume.domain.SectionLabel;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeChunkRepository;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeRepository;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeVersionRepository;
import com.jobhuntai.jobhunt_backend.skillgap.client.GapAnalysisClientRequest;
import com.jobhuntai.jobhunt_backend.skillgap.client.GapAnalysisClientResult;
import com.jobhuntai.jobhunt_backend.skillgap.client.GapItemResult;
import com.jobhuntai.jobhunt_backend.skillgap.client.SkillGapIntelligenceClient;
import com.jobhuntai.jobhunt_backend.skillgap.client.StandaloneGapClientResult;
import com.jobhuntai.jobhunt_backend.skillgap.domain.SkillGap;
import com.jobhuntai.jobhunt_backend.skillgap.domain.SkillGapStatus;
import com.jobhuntai.jobhunt_backend.skillgap.dto.SkillGapResponse;
import com.jobhuntai.jobhunt_backend.skillgap.dto.StandaloneGapResponse;
import com.jobhuntai.jobhunt_backend.skillgap.extractor.GapExtractor;
import com.jobhuntai.jobhunt_backend.skillgap.repository.SkillGapRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkillGapServiceTest {

    @Mock private MatchResultRepository matchResultRepository;
    @Mock private JdIntelligenceRepository jdIntelligenceRepository;
    @Mock private ResumeRepository resumeRepository;
    @Mock private ResumeVersionRepository resumeVersionRepository;
    @Mock private ResumeChunkRepository resumeChunkRepository;
    @Mock private SkillGapRepository skillGapRepository;
    @Mock private SkillGapPersistenceService persistenceService;
    @Mock private SkillGapIntelligenceClient intelligenceClient;

    private SkillGapService skillGapService;

    private final UUID userId = UUID.randomUUID();
    private final UUID matchId = UUID.randomUUID();
    private final UUID resumeId = UUID.randomUUID();
    private final UUID jdId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();

    /** Status of the entity at the moment of each save(), in order. */
    private final List<SkillGapStatus> savedStatuses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        savedStatuses.clear();
        // The extractor is real: deriving the gap lists from a match is the logic
        // under test on the happy path, and stubbing it would assert nothing.
        skillGapService = new SkillGapService(
                matchResultRepository, jdIntelligenceRepository, resumeRepository,
                resumeVersionRepository, resumeChunkRepository, skillGapRepository,
                persistenceService, intelligenceClient, new GapExtractor());

        when(matchResultRepository.findByIdAndUserId(matchId, userId))
                .thenReturn(Optional.of(matchWithGaps()));
        when(jdIntelligenceRepository.findByJobDescriptionId(jdId))
                .thenReturn(Optional.of(jdIntelligence()));
        when(resumeRepository.findByIdAndUserId(resumeId, userId))
                .thenReturn(Optional.of(Resume.builder().id(resumeId).userId(userId).build()));
        when(resumeVersionRepository.findTopByResumeIdOrderByVersionNumberDesc(resumeId))
                .thenReturn(Optional.of(ResumeVersion.builder()
                        .id(versionId).resumeId(resumeId).versionNumber(1).rawText("raw").build()));
        when(resumeChunkRepository.findAllByResumeVersionIdOrderByChunkIndex(versionId))
                .thenReturn(List.of(ResumeChunk.builder()
                        .id(UUID.randomUUID()).resumeVersionId(versionId).chunkIndex(0)
                        .sectionLabel(SectionLabel.SKILLS).content("Java, Spring Boot, AWS").build()));
        when(skillGapRepository.findByMatchResultId(matchId)).thenReturn(Optional.empty());

        // save() echoes its argument back and records the status at call time — a
        // captor would hold two references to the same mutated instance.
        when(persistenceService.save(any(SkillGap.class))).thenAnswer(invocation -> {
            SkillGap argument = invocation.getArgument(0);
            savedStatuses.add(argument.getStatus());
            return argument;
        });
    }

    private MatchResult matchWithGaps() {
        return MatchResult.builder()
                .id(matchId)
                .userId(userId)
                .resumeId(resumeId)
                .jobDescriptionId(jdId)
                .overallScore(new BigDecimal("74.50"))
                .mustHaveMissing(JdMapper.serializeList(List.of("Production Kubernetes")))
                .skillsMissing(JdMapper.serializeList(List.of("Terraform")))
                .skillsMatched(JdMapper.serializeList(List.of("Java")))
                .build();
    }

    private MatchResult matchWithNoGaps() {
        return MatchResult.builder()
                .id(matchId)
                .userId(userId)
                .resumeId(resumeId)
                .jobDescriptionId(jdId)
                .overallScore(new BigDecimal("98.00"))
                .mustHaveMissing(JdMapper.serializeList(List.of()))
                .skillsMissing(JdMapper.serializeList(List.of()))
                .skillsMatched(JdMapper.serializeList(List.of("Java", "GraphQL")))
                .build();
    }

    private JdIntelligence jdIntelligence() {
        return JdIntelligence.builder()
                .id(UUID.randomUUID())
                .jobDescriptionId(jdId)
                .jobTitle("Senior Backend Engineer")
                .companyName("Fintech Global")
                .preferredSkills(JdMapper.serializeList(List.of("GraphQL")))
                .build();
    }

    private GapAnalysisClientResult clientResult() {
        return new GapAnalysisClientResult(
                "Orchestration is the main gap.",
                List.of(
                        new GapItemResult("Production Kubernetes", "CRITICAL",
                                "Named as a must-have.", "Learn pod scheduling and Helm charts.", 6),
                        new GapItemResult("Terraform", "HIGH",
                                "Required skill.", "Codify your AWS setup as modules.", 3),
                        new GapItemResult("GraphQL", "MEDIUM",
                                "Preferred.", "Add a GraphQL layer to your REST API.", 1)),
                List.of("GraphQL"),
                List.of("Production Kubernetes"));
    }

    @Test
    void happyPath_completesWithGapsSummaryAndTimestamp() {
        when(intelligenceClient.analyzeGap(any())).thenReturn(clientResult());

        SkillGapResponse response = skillGapService.analyzeMatchGap(userId, matchId);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.lastAnalyzedAt()).isNotNull();
        assertThat(response.gapSummary()).isEqualTo("Orchestration is the main gap.");
        assertThat(response.gaps()).hasSize(3);
        assertThat(response.gaps().getFirst().priority()).isEqualTo("CRITICAL");
        assertThat(response.gaps().getFirst().learningRecommendation()).contains("Helm");
        assertThat(response.quickWins()).containsExactly("GraphQL");
        assertThat(response.dealBreakers()).containsExactly("Production Kubernetes");
        assertThat(response.overallScoreContext()).isEqualByComparingTo("74.50");

        // Saved twice: ANALYZING before the LLM call, then COMPLETED after.
        assertThat(savedStatuses)
                .containsExactly(SkillGapStatus.ANALYZING, SkillGapStatus.COMPLETED);
    }

    @Test
    void gapsDerivedFromTheMatchAreSentToTheIntelligenceService() {
        when(intelligenceClient.analyzeGap(any())).thenReturn(clientResult());

        skillGapService.analyzeMatchGap(userId, matchId);

        ArgumentCaptor<GapAnalysisClientRequest> request =
                ArgumentCaptor.forClass(GapAnalysisClientRequest.class);
        verify(intelligenceClient).analyzeGap(request.capture());

        GapAnalysisClientRequest sent = request.getValue();
        assertThat(sent.missingMustHaves()).containsExactly("Production Kubernetes");
        assertThat(sent.missingSkills()).containsExactly("Terraform");
        // GraphQL is preferred and unmatched, so it is reconstructed as a MEDIUM gap.
        assertThat(sent.preferredMissing()).containsExactly("GraphQL");
        // Role context the prompt needs to make recommendations role-specific.
        assertThat(sent.jobTitle()).isEqualTo("Senior Backend Engineer");
        assertThat(sent.companyName()).isEqualTo("Fintech Global");
        assertThat(sent.overallScore()).isEqualTo(74.5);
        assertThat(sent.resumeTextSummary()).contains("Spring Boot");
    }

    @Test
    void perfectMatch_shortCircuitsTheLlmCall() {
        when(matchResultRepository.findByIdAndUserId(matchId, userId))
                .thenReturn(Optional.of(matchWithNoGaps()));

        SkillGapResponse response = skillGapService.analyzeMatchGap(userId, matchId);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.gaps()).isEmpty();
        assertThat(response.quickWins()).isEmpty();
        assertThat(response.dealBreakers()).isEmpty();
        assertThat(response.gapSummary()).isEqualTo(SkillGapService.NO_GAPS_SUMMARY);
        // Asking a model to explain an empty gap list invites it to invent one.
        verify(intelligenceClient, never()).analyzeGap(any());
    }

    @Test
    void intelligenceUnavailable_persistsFailedAndPropagates() {
        when(intelligenceClient.analyzeGap(any()))
                .thenThrow(new IntelligenceServiceUnavailableException("down"));

        assertThatThrownBy(() -> skillGapService.analyzeMatchGap(userId, matchId))
                .isInstanceOf(IntelligenceServiceUnavailableException.class);

        assertThat(savedStatuses)
                .containsExactly(SkillGapStatus.ANALYZING, SkillGapStatus.FAILED);

        ArgumentCaptor<SkillGap> saved = ArgumentCaptor.forClass(SkillGap.class);
        verify(persistenceService, times(2)).save(saved.capture());
        assertThat(saved.getValue().getAnalysisError()).isEqualTo("Intelligence service unavailable");
    }

    @Test
    void gapAnalysisFailed_persistsFailedAndPropagates() {
        when(intelligenceClient.analyzeGap(any()))
                .thenThrow(new GapAnalysisFailedException("Gap analysis failed"));

        assertThatThrownBy(() -> skillGapService.analyzeMatchGap(userId, matchId))
                .isInstanceOf(GapAnalysisFailedException.class);

        assertThat(savedStatuses)
                .containsExactly(SkillGapStatus.ANALYZING, SkillGapStatus.FAILED);

        ArgumentCaptor<SkillGap> saved = ArgumentCaptor.forClass(SkillGap.class);
        verify(persistenceService, times(2)).save(saved.capture());
        assertThat(saved.getValue().getAnalysisError()).isEqualTo("Gap analysis failed");
    }

    @Test
    void matchNotFoundOrNotOwned_throwsResourceNotFound() {
        when(matchResultRepository.findByIdAndUserId(matchId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> skillGapService.analyzeMatchGap(userId, matchId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(matchId.toString());
        verify(persistenceService, never()).save(any());
    }

    @Test
    void jdNotExtracted_throwsJdIntelligenceNotFound() {
        when(jdIntelligenceRepository.findByJobDescriptionId(jdId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> skillGapService.analyzeMatchGap(userId, matchId))
                .isInstanceOf(JdIntelligenceNotFoundException.class);
    }

    @Test
    void reanalysis_upsertsExistingRowAndAdvancesLastAnalyzedAt() {
        UUID existingId = UUID.randomUUID();
        Instant previousRun = Instant.now().minusSeconds(3600);
        SkillGap existing = SkillGap.builder()
                .id(existingId)
                .userId(userId)
                .matchResultId(matchId)
                .resumeId(resumeId)
                .jobDescriptionId(jdId)
                .status(SkillGapStatus.COMPLETED)
                .lastAnalyzedAt(previousRun)
                .persisted(true)
                .build();
        when(skillGapRepository.findByMatchResultId(matchId)).thenReturn(Optional.of(existing));
        when(intelligenceClient.analyzeGap(any())).thenReturn(clientResult());

        SkillGapResponse response = skillGapService.analyzeMatchGap(userId, matchId);

        // Same row, new content — not a second analysis for the same match.
        assertThat(response.id()).isEqualTo(existingId);
        assertThat(response.lastAnalyzedAt()).isAfter(previousRun);
        verify(skillGapRepository).findByMatchResultId(matchId);
    }

    @Test
    void getSkillGapByMatch_withoutPriorAnalysis_throwsSkillGapNotFound() {
        assertThatThrownBy(() -> skillGapService.getSkillGapByMatch(matchId, userId))
                .isInstanceOf(SkillGapNotFoundException.class);
    }

    @Test
    void getSkillGap_wrongUser_throwsResourceNotFound() {
        UUID gapId = UUID.randomUUID();
        when(skillGapRepository.findByIdAndUserId(gapId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> skillGapService.getSkillGap(gapId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getSkillGapsForResume_checksOwnershipBeforeListing() {
        when(resumeRepository.findByIdAndUserId(resumeId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> skillGapService.getSkillGapsForResume(resumeId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void standaloneAnalysis_returnsResponseAndPersistsNothing() {
        when(intelligenceClient.analyzeStandalone(anyString())).thenReturn(
                new StandaloneGapClientResult(
                        List.of("backend API development"),
                        List.of("data engineering"),
                        List.of("Kafka Streams", "Spark"),
                        "A solid backend engineer."));

        StandaloneGapResponse response = skillGapService.analyzeResumeStandalone(userId, resumeId);

        assertThat(response.resumeId()).isEqualTo(resumeId);
        assertThat(response.strongDomains()).containsExactly("backend API development");
        assertThat(response.weakDomains()).containsExactly("data engineering");
        // Nothing is stored: the assessment derives from the resume alone and would
        // go stale the moment that changed.
        verify(persistenceService, never()).save(any());
    }

    @Test
    void standaloneAnalysis_wrongUser_throwsResourceNotFound() {
        when(resumeRepository.findByIdAndUserId(resumeId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> skillGapService.analyzeResumeStandalone(userId, resumeId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(intelligenceClient, never()).analyzeStandalone(anyString());
    }
}
