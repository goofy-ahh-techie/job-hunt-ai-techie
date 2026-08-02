package com.jobhuntai.jobhunt_backend.matching.scoring;

import com.jobhuntai.jobhunt_backend.resume.domain.SectionLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.chunk;
import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.context;
import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.jd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubScoreOrchestratorTest {

    @Mock private MustHaveCoverageScorer mustHaveCoverageScorer;
    @Mock private RequiredSkillsScorer requiredSkillsScorer;
    @Mock private ResponsibilitiesOverlapScorer responsibilitiesOverlapScorer;
    @Mock private ExperienceLevelScorer experienceLevelScorer;
    @Mock private QualificationsScorer qualificationsScorer;
    @Mock private PreferredSkillsScorer preferredSkillsScorer;

    @InjectMocks private SubScoreOrchestrator orchestrator;

    private final ScoringContext context =
            context(jd().build(), chunk(SectionLabel.SKILLS, "Java"));

    private void stubAll(double score) {
        when(mustHaveCoverageScorer.score(any())).thenReturn(SubScoreResult.scoreOnly(score, "ok"));
        when(requiredSkillsScorer.score(any())).thenReturn(SubScoreResult.scoreOnly(score, "ok"));
        when(responsibilitiesOverlapScorer.score(any())).thenReturn(SubScoreResult.scoreOnly(score, "ok"));
        when(experienceLevelScorer.score(any())).thenReturn(SubScoreResult.scoreOnly(score, "ok"));
        when(qualificationsScorer.score(any())).thenReturn(SubScoreResult.scoreOnly(score, "ok"));
        when(preferredSkillsScorer.score(any())).thenReturn(SubScoreResult.scoreOnly(score, "ok"));
    }

    @Test
    void allScorersComplete_allResultsCollected() {
        stubAll(80.0);

        OrchestratedScores scores = orchestrator.orchestrate(context);

        assertThat(scores.mustHave().rawScore()).isEqualTo(80.0);
        assertThat(scores.requiredSkills().rawScore()).isEqualTo(80.0);
        assertThat(scores.responsibilities().rawScore()).isEqualTo(80.0);
        assertThat(scores.experience().rawScore()).isEqualTo(80.0);
        assertThat(scores.qualifications().rawScore()).isEqualTo(80.0);
        assertThat(scores.preferredSkills().rawScore()).isEqualTo(80.0);
    }

    @Test
    void oneScorerThrows_itScoresZeroAndTheOthersStillRun() {
        stubAll(90.0);
        when(experienceLevelScorer.score(any()))
                .thenThrow(new IllegalStateException("regex blew up"));

        OrchestratedScores scores = orchestrator.orchestrate(context);

        assertThat(scores.experience().rawScore()).isEqualTo(0.0);
        assertThat(scores.experience().explanation())
                .isEqualTo("Scorer failed: regex blew up");

        // The failure is contained to its own dimension.
        assertThat(scores.mustHave().rawScore()).isEqualTo(90.0);
        assertThat(scores.requiredSkills().rawScore()).isEqualTo(90.0);
        assertThat(scores.responsibilities().rawScore()).isEqualTo(90.0);
        assertThat(scores.qualifications().rawScore()).isEqualTo(90.0);
        assertThat(scores.preferredSkills().rawScore()).isEqualTo(90.0);

        // Crucially, the scorers after the failing one were still invoked.
        verify(qualificationsScorer).score(any());
        verify(preferredSkillsScorer).score(any());
    }

    @Test
    void exceptionWithoutAMessage_stillProducesAReadableExplanation() {
        stubAll(50.0);
        when(mustHaveCoverageScorer.score(any())).thenThrow(new NullPointerException());

        OrchestratedScores scores = orchestrator.orchestrate(context);

        assertThat(scores.mustHave().rawScore()).isEqualTo(0.0);
        assertThat(scores.mustHave().explanation())
                .isEqualTo("Scorer failed: NullPointerException");
    }
}
