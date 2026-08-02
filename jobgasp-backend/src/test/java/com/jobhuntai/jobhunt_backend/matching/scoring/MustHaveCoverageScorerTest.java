package com.jobhuntai.jobhunt_backend.matching.scoring;

import com.jobhuntai.jobhunt_backend.common.exception.IntelligenceServiceUnavailableException;
import com.jobhuntai.jobhunt_backend.matching.client.MatchingIntelligenceClient;
import com.jobhuntai.jobhunt_backend.resume.domain.SectionLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.chunk;
import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.context;
import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.jd;
import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.json;
import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.semanticResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MustHaveCoverageScorerTest {

    @Mock private MatchingIntelligenceClient intelligenceClient;

    private MustHaveCoverageScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new MustHaveCoverageScorer(
                intelligenceClient, MustHaveCoverageScorer.DEFAULT_SEMANTIC_THRESHOLD);
        // Default: the semantic pass rescues nothing, so these tests exercise the
        // keyword pass alone unless they say otherwise.
        when(intelligenceClient.semanticSimilarity(anyList(), anyList(), anyDouble()))
                .thenAnswer(invocation -> semanticResult(invocation.getArgument(0), List.of()));
    }

    @Test
    void allMustHavesFound_scoresHundred() {
        ScoringContext context = context(
                jd().mustHave(json(List.of("Java", "Spring Boot"))).build(),
                chunk(SectionLabel.SKILLS, "Java, Spring Boot, PostgreSQL"));

        SubScoreResult result = scorer.score(context);

        assertThat(result.rawScore()).isEqualTo(100.0);
        assertThat(result.matched()).containsExactly("Java", "Spring Boot");
        assertThat(result.missing()).isEmpty();
        assertThat(result.explanation()).isEqualTo("2 of 2 must-have requirements found in resume");
    }

    @Test
    void noMustHavesFound_scoresZeroAndReportsAllMissing() {
        ScoringContext context = context(
                jd().mustHave(json(List.of("Kubernetes", "Terraform"))).build(),
                chunk(SectionLabel.SKILLS, "Java, Spring Boot"));

        SubScoreResult result = scorer.score(context);

        assertThat(result.rawScore()).isEqualTo(0.0);
        assertThat(result.matched()).isEmpty();
        assertThat(result.missing()).containsExactly("Kubernetes", "Terraform");
    }

    @Test
    void partialMatch_scoresProportionally() {
        ScoringContext context = context(
                jd().mustHave(json(List.of("Java", "Kubernetes", "Kafka", "Terraform"))).build(),
                chunk(SectionLabel.SKILLS, "Java and Kafka experience"));

        SubScoreResult result = scorer.score(context);

        assertThat(result.rawScore()).isEqualTo(50.0);
        assertThat(result.matched()).containsExactly("Java", "Kafka");
        assertThat(result.missing()).containsExactly("Kubernetes", "Terraform");
    }

    @Test
    void matchingIsCaseInsensitiveAcrossAllChunks() {
        ScoringContext context = context(
                jd().mustHave(json(List.of("SPRING BOOT", "postgresql"))).build(),
                chunk(SectionLabel.SUMMARY, "Backend engineer building Spring Boot services"),
                chunk(SectionLabel.SKILLS, "PostgreSQL, Redis"));

        SubScoreResult result = scorer.score(context);

        assertThat(result.rawScore()).isEqualTo(100.0);
    }

    @Test
    void sentenceShapedMustHavesAreRescuedByTheSemanticPass() {
        // The real shape of Phase 3 output: must-haves are sentences, not keywords,
        // and no resume contains them verbatim. Keyword-only scored 0 of 4 here.
        List<String> sentenceMustHaves = List.of(
                "Strong experience with Java and Spring Boot in production",
                "Hands-on experience with Kubernetes in production",
                "Experience owning services end to end");
        when(intelligenceClient.semanticSimilarity(anyList(), anyList(), anyDouble()))
                .thenReturn(semanticResult(sentenceMustHaves, List.of(
                        "Strong experience with Java and Spring Boot in production",
                        "Experience owning services end to end")));

        ScoringContext context = context(
                jd().mustHave(json(sentenceMustHaves)).build(),
                chunk(SectionLabel.EXPERIENCE,
                        "Built and owned production Java services on Spring Boot end to end."));

        SubScoreResult result = scorer.score(context);

        assertThat(result.rawScore()).isEqualTo(200.0 / 3);
        assertThat(result.missing())
                .containsExactly("Hands-on experience with Kubernetes in production");
        assertThat(result.explanation()).contains("(2 via semantic)");
    }

    @Test
    void serviceUnavailable_keepsKeywordScoreWithDegradedNote() {
        when(intelligenceClient.semanticSimilarity(anyList(), anyList(), anyDouble()))
                .thenThrow(new IntelligenceServiceUnavailableException("down"));

        ScoringContext context = context(
                jd().mustHave(json(List.of("Java", "Kubernetes"))).build(),
                chunk(SectionLabel.SKILLS, "Java, Spring Boot"));

        SubScoreResult result = scorer.score(context);

        assertThat(result.rawScore()).isEqualTo(50.0);
        assertThat(result.explanation()).contains(MustHaveCoverageScorer.DEGRADED_NOTE);
    }

    @Test
    void emptyMustHaveList_scoresHundredWithExplanation() {
        ScoringContext context = context(
                jd().mustHave(json(List.of())).build(),
                chunk(SectionLabel.SKILLS, "Java"));

        SubScoreResult result = scorer.score(context);

        assertThat(result.rawScore()).isEqualTo(100.0);
        assertThat(result.explanation()).isEqualTo("No must-have requirements specified");
        assertThat(result.matched()).isEmpty();
        assertThat(result.missing()).isEmpty();
        verify(intelligenceClient, never()).semanticSimilarity(anyList(), anyList(), anyDouble());
    }
}
