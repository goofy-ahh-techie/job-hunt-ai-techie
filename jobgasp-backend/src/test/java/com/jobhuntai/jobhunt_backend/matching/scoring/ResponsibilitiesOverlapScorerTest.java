package com.jobhuntai.jobhunt_backend.matching.scoring;

import com.jobhuntai.jobhunt_backend.common.exception.IntelligenceServiceUnavailableException;
import com.jobhuntai.jobhunt_backend.matching.client.MatchingIntelligenceClient;
import com.jobhuntai.jobhunt_backend.resume.domain.SectionLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.chunk;
import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.context;
import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.jd;
import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.json;
import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.semanticResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResponsibilitiesOverlapScorerTest {

    @Mock private MatchingIntelligenceClient intelligenceClient;

    private ResponsibilitiesOverlapScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new ResponsibilitiesOverlapScorer(
                intelligenceClient, ResponsibilitiesOverlapScorer.DEFAULT_SEMANTIC_THRESHOLD);
    }

    private static final List<String> RESPONSIBILITIES =
            List.of("Own service reliability", "Mentor junior engineers");

    @Test
    void experienceChunksAreUsedWhenPresent() {
        when(intelligenceClient.semanticSimilarity(anyList(), anyList(), anyDouble()))
                .thenReturn(semanticResult(RESPONSIBILITIES, List.of("Own service reliability")));

        ScoringContext context = context(
                jd().responsibilities(json(RESPONSIBILITIES)).build(),
                chunk(SectionLabel.EXPERIENCE, "Ran on-call and drove uptime from 99.0 to 99.95"),
                chunk(SectionLabel.SKILLS, "Java, Kafka"),
                chunk(SectionLabel.EDUCATION, "B.Tech Computer Science"));

        SubScoreResult result = scorer.score(context);

        assertThat(result.rawScore()).isEqualTo(50.0);
        assertThat(result.matched()).containsExactly("Own service reliability");
        assertThat(result.missing()).containsExactly("Mentor junior engineers");

        ArgumentCaptor<List<String>> targets = ArgumentCaptor.captor();
        verify(intelligenceClient).semanticSimilarity(anyList(), targets.capture(), anyDouble());
        assertThat(targets.getValue())
                .containsExactly("Ran on-call and drove uptime from 99.0 to 99.95");
    }

    @Test
    void fallsBackToAllChunksWhenResumeHasNoExperienceSection() {
        when(intelligenceClient.semanticSimilarity(anyList(), anyList(), anyDouble()))
                .thenReturn(semanticResult(RESPONSIBILITIES, List.of()));

        ScoringContext context = context(
                jd().responsibilities(json(RESPONSIBILITIES)).build(),
                chunk(SectionLabel.SKILLS, "Java, Kafka"),
                chunk(SectionLabel.OTHER, "Various projects"));

        scorer.score(context);

        // No EXPERIENCE chunk exists, so every chunk is offered rather than none —
        // a chunker miss must not be scored as an absence of experience.
        ArgumentCaptor<List<String>> targets = ArgumentCaptor.captor();
        verify(intelligenceClient).semanticSimilarity(anyList(), targets.capture(), anyDouble());
        assertThat(targets.getValue()).containsExactly("Java, Kafka", "Various projects");
    }

    @Test
    void usesTheProseThresholdWhichSitsAboveTheSkillsOne() {
        when(intelligenceClient.semanticSimilarity(anyList(), anyList(), anyDouble()))
                .thenReturn(semanticResult(RESPONSIBILITIES, List.of()));

        scorer.score(context(
                jd().responsibilities(json(RESPONSIBILITIES)).build(),
                chunk(SectionLabel.EXPERIENCE, "Built things")));

        ArgumentCaptor<Double> threshold = ArgumentCaptor.captor();
        verify(intelligenceClient).semanticSimilarity(anyList(), anyList(), threshold.capture());
        assertThat(threshold.getValue())
                .isEqualTo(ResponsibilitiesOverlapScorer.DEFAULT_SEMANTIC_THRESHOLD);
        // Prose-to-prose is the easier comparison, so it can demand a higher score
        // than a bare skill noun matched against a paragraph. Calibrated live.
        assertThat(threshold.getValue())
                .isGreaterThan(RequiredSkillsScorer.DEFAULT_SEMANTIC_THRESHOLD);
    }

    @Test
    void serviceUnavailable_fallsBackToKeywordMatching() {
        when(intelligenceClient.semanticSimilarity(anyList(), anyList(), anyDouble()))
                .thenThrow(new IntelligenceServiceUnavailableException("down"));

        ScoringContext context = context(
                jd().responsibilities(json(RESPONSIBILITIES)).build(),
                chunk(SectionLabel.EXPERIENCE, "Own service reliability for the payments API"));

        SubScoreResult result = scorer.score(context);

        // Keyword containment rescues the one responsibility quoted verbatim.
        assertThat(result.rawScore()).isEqualTo(50.0);
        assertThat(result.matched()).containsExactly("Own service reliability");
        assertThat(result.explanation()).contains(ResponsibilitiesOverlapScorer.DEGRADED_NOTE);
    }

    @Test
    void emptyResponsibilities_scoresHundred() {
        SubScoreResult result = scorer.score(context(
                jd().responsibilities(json(List.of())).build(),
                chunk(SectionLabel.EXPERIENCE, "Did work")));

        assertThat(result.rawScore()).isEqualTo(100.0);
        assertThat(result.explanation()).isEqualTo("No responsibilities specified");
    }
}
