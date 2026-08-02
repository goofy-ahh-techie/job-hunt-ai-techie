package com.jobhuntai.jobhunt_backend.matching.service;

import com.jobhuntai.jobhunt_backend.common.exception.JdIntelligenceNotFoundException;
import com.jobhuntai.jobhunt_backend.common.exception.MatchCalculationFailedException;
import com.jobhuntai.jobhunt_backend.common.exception.ResourceNotFoundException;
import com.jobhuntai.jobhunt_backend.common.exception.ResumeVersionNotFoundException;
import com.jobhuntai.jobhunt_backend.jd.domain.JdIntelligence;
import com.jobhuntai.jobhunt_backend.jd.domain.JobDescription;
import com.jobhuntai.jobhunt_backend.jd.repository.JdIntelligenceRepository;
import com.jobhuntai.jobhunt_backend.jd.repository.JobDescriptionRepository;
import com.jobhuntai.jobhunt_backend.matching.domain.MatchResult;
import com.jobhuntai.jobhunt_backend.matching.domain.MatchStatus;
import com.jobhuntai.jobhunt_backend.matching.dto.MatchRequest;
import com.jobhuntai.jobhunt_backend.matching.dto.MatchResponse;
import com.jobhuntai.jobhunt_backend.matching.repository.MatchResultRepository;
import com.jobhuntai.jobhunt_backend.matching.scoring.OrchestratedScores;
import com.jobhuntai.jobhunt_backend.matching.scoring.ScoreExplanationBuilder;
import com.jobhuntai.jobhunt_backend.matching.scoring.SubScoreOrchestrator;
import com.jobhuntai.jobhunt_backend.matching.scoring.SubScoreResult;
import com.jobhuntai.jobhunt_backend.matching.scoring.WeightedScoreCalculator;
import com.jobhuntai.jobhunt_backend.resume.domain.Resume;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeChunk;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeVersion;
import com.jobhuntai.jobhunt_backend.resume.domain.SectionLabel;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeChunkRepository;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeRepository;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeVersionRepository;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchServiceTest {

    @Mock private ResumeRepository resumeRepository;
    @Mock private ResumeVersionRepository resumeVersionRepository;
    @Mock private ResumeChunkRepository resumeChunkRepository;
    @Mock private JobDescriptionRepository jobDescriptionRepository;
    @Mock private JdIntelligenceRepository jdIntelligenceRepository;
    @Mock private MatchResultRepository matchResultRepository;
    @Mock private MatchPersistenceService persistenceService;
    @Mock private SubScoreOrchestrator orchestrator;
    @Mock private ScoreExplanationBuilder explanationBuilder;

    private MatchService matchService;

    private final UUID userId = UUID.randomUUID();
    private final UUID resumeId = UUID.randomUUID();
    private final UUID jdId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final MatchRequest request = new MatchRequest(resumeId, jdId);

    /** Status of the entity at the moment of each save(), in order. */
    private final List<MatchStatus> savedStatuses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        savedStatuses.clear();
        // The calculator is real: its arithmetic is the thing under test in the
        // happy path, and stubbing it would assert nothing about the actual score.
        matchService = new MatchService(
                resumeRepository, resumeVersionRepository, resumeChunkRepository,
                jobDescriptionRepository, jdIntelligenceRepository, matchResultRepository,
                persistenceService, orchestrator, new WeightedScoreCalculator(),
                explanationBuilder);

        when(resumeRepository.findByIdAndUserId(resumeId, userId))
                .thenReturn(Optional.of(Resume.builder().id(resumeId).userId(userId).build()));
        when(resumeVersionRepository.findTopByResumeIdOrderByVersionNumberDesc(resumeId))
                .thenReturn(Optional.of(ResumeVersion.builder()
                        .id(versionId).resumeId(resumeId).versionNumber(1).rawText("raw").build()));
        when(resumeChunkRepository.findAllByResumeVersionIdOrderByChunkIndex(versionId))
                .thenReturn(List.of(ResumeChunk.builder()
                        .id(UUID.randomUUID()).resumeVersionId(versionId).chunkIndex(0)
                        .sectionLabel(SectionLabel.SKILLS).content("Java, Kafka").build()));
        when(jobDescriptionRepository.findByIdAndUserId(jdId, userId))
                .thenReturn(Optional.of(JobDescription.builder().id(jdId).userId(userId).build()));
        when(jdIntelligenceRepository.findByJobDescriptionId(jdId))
                .thenReturn(Optional.of(JdIntelligence.builder()
                        .id(UUID.randomUUID()).jobDescriptionId(jdId).build()));
        when(matchResultRepository.findByResumeVersionIdAndJobDescriptionId(versionId, jdId))
                .thenReturn(Optional.empty());

        // save() echoes its argument back, like a real insert of an assigned id.
        // The status at each call is recorded here rather than via an ArgumentCaptor:
        // the service mutates one instance across both saves, so a captor would hold
        // two references to the same object and report the final status twice.
        when(persistenceService.save(any(MatchResult.class)))
                .thenAnswer(invocation -> {
                    MatchResult argument = invocation.getArgument(0);
                    savedStatuses.add(argument.getStatus());
                    return argument;
                });
        when(explanationBuilder.build(any(), org.mockito.ArgumentMatchers.anyDouble()))
                .thenReturn("Overall match: 75.80/100");
    }

    private void stubScores(double each) {
        when(orchestrator.orchestrate(any())).thenReturn(new OrchestratedScores(
                SubScoreResult.of(each, List.of("Java"), List.of("Kubernetes"), "must-have"),
                SubScoreResult.of(each, List.of("Java"), List.of("Terraform"), "skills"),
                SubScoreResult.scoreOnly(each, "responsibilities"),
                SubScoreResult.scoreOnly(each, "experience"),
                SubScoreResult.scoreOnly(each, "qualifications"),
                SubScoreResult.of(each, List.of("Kafka"), List.of(), "preferred")));
    }

    @Test
    void happyPath_completesWithScoresExplanationAndTimestamp() {
        stubScores(80.0);

        MatchResponse response = matchService.calculateMatch(userId, request);

        assertThat(response.status()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(response.lastCalculatedAt()).isNotNull();
        // Every dimension at 80 weights to 80 exactly, since the weights sum to 1.
        assertThat(response.overallScore()).isEqualByComparingTo("80.00");
        assertThat(response.scoreExplanation()).isEqualTo("Overall match: 75.80/100");
        assertThat(response.resumeVersionId()).isEqualTo(versionId);
        assertThat(response.mustHave().missing()).containsExactly("Kubernetes");
        assertThat(response.requiredSkills().missing()).containsExactly("Terraform");

        // Saved twice: CALCULATING before scoring, then COMPLETED after.
        ArgumentCaptor<MatchResult> saved = ArgumentCaptor.forClass(MatchResult.class);
        verify(persistenceService, times(2)).save(saved.capture());
        assertThat(savedStatuses)
                .containsExactly(MatchStatus.CALCULATING, MatchStatus.COMPLETED);

        MatchResult finalSave = saved.getAllValues().getLast();
        assertThat(finalSave.getStatus()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(finalSave.getSkillsMissing()).contains("Terraform");
        assertThat(finalSave.getMustHaveMissing()).contains("Kubernetes");
        assertThat(finalSave.getPreferredMatched()).contains("Kafka");
    }

    @Test
    void resumeNotFoundOrNotOwned_throwsResourceNotFound() {
        when(resumeRepository.findByIdAndUserId(resumeId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.calculateMatch(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(resumeId.toString());
    }

    @Test
    void resumeHasNoParsedVersion_throwsResumeVersionNotFound() {
        when(resumeVersionRepository.findTopByResumeIdOrderByVersionNumberDesc(resumeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.calculateMatch(userId, request))
                .isInstanceOf(ResumeVersionNotFoundException.class);
    }

    @Test
    void jdNotFoundOrNotOwned_throwsResourceNotFound() {
        when(jobDescriptionRepository.findByIdAndUserId(jdId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.calculateMatch(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(jdId.toString());
    }

    @Test
    void jdNotYetExtracted_throwsJdIntelligenceNotFound() {
        when(jdIntelligenceRepository.findByJobDescriptionId(jdId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.calculateMatch(userId, request))
                .isInstanceOf(JdIntelligenceNotFoundException.class);
    }

    @Test
    void scoringFailure_recordsFailedStatusAndPropagatesAsMatchCalculationFailed() {
        when(orchestrator.orchestrate(any())).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> matchService.calculateMatch(userId, request))
                .isInstanceOf(MatchCalculationFailedException.class)
                .hasMessageContaining("boom");

        // The row must not be left parked in CALCULATING.
        verify(persistenceService, atLeastOnce()).save(any(MatchResult.class));
        assertThat(savedStatuses)
                .containsExactly(MatchStatus.CALCULATING, MatchStatus.FAILED);
    }

    @Test
    void recalculation_upsertsExistingRowAndAdvancesLastCalculatedAt() {
        UUID existingId = UUID.randomUUID();
        Instant previousRun = Instant.now().minusSeconds(3600);
        MatchResult existing = MatchResult.builder()
                .id(existingId)
                .userId(userId)
                .resumeId(resumeId)
                .resumeVersionId(versionId)
                .jobDescriptionId(jdId)
                .overallScore(new BigDecimal("42.00"))
                .mustHaveScore(BigDecimal.ZERO)
                .requiredSkillsScore(BigDecimal.ZERO)
                .responsibilitiesScore(BigDecimal.ZERO)
                .experienceScore(BigDecimal.ZERO)
                .qualificationsScore(BigDecimal.ZERO)
                .preferredSkillsScore(BigDecimal.ZERO)
                .status(MatchStatus.COMPLETED)
                .lastCalculatedAt(previousRun)
                .persisted(true)
                .build();
        when(matchResultRepository.findByResumeVersionIdAndJobDescriptionId(versionId, jdId))
                .thenReturn(Optional.of(existing));
        stubScores(90.0);

        MatchResponse response = matchService.calculateMatch(userId, request);

        // Same row, new numbers — not a second result for the same pair.
        assertThat(response.id()).isEqualTo(existingId);
        assertThat(response.overallScore()).isEqualByComparingTo("90.00");
        assertThat(response.lastCalculatedAt()).isAfter(previousRun);
        verify(matchResultRepository).findByResumeVersionIdAndJobDescriptionId(versionId, jdId);
    }

    @Test
    void getMatch_wrongUser_throwsResourceNotFound() {
        UUID matchId = UUID.randomUUID();
        when(matchResultRepository.findByIdAndUserId(matchId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.getMatch(matchId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getMatchesForResume_checksOwnershipBeforeListing() {
        when(resumeRepository.findByIdAndUserId(resumeId, userId)).thenReturn(Optional.empty());

        // An unowned resume id must 404, not return an empty list that would confirm
        // the id exists elsewhere.
        assertThatThrownBy(() -> matchService.getMatchesForResume(resumeId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
