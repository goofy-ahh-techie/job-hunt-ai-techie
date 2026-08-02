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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequiredSkillsScorerTest {

    @Mock private MatchingIntelligenceClient intelligenceClient;

    private RequiredSkillsScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new RequiredSkillsScorer(
                intelligenceClient, RequiredSkillsScorer.DEFAULT_SEMANTIC_THRESHOLD);
    }

    @Test
    void serviceUnavailable_fallsBackToKeywordScoreWithDegradedNote() {
        when(intelligenceClient.semanticSimilarity(anyList(), anyList(), anyDouble()))
                .thenThrow(new IntelligenceServiceUnavailableException("down"));

        ScoringContext context = context(
                jd().requiredSkills(json(List.of("Java", "Kubernetes"))).build(),
                chunk(SectionLabel.SKILLS, "Java, Spring Boot"));

        SubScoreResult result = scorer.score(context);

        // Keyword pass found 1 of 2; the semantic pass could not rescue Kubernetes.
        assertThat(result.rawScore()).isEqualTo(50.0);
        assertThat(result.matched()).containsExactly("Java");
        assertThat(result.missing()).containsExactly("Kubernetes");
        assertThat(result.explanation()).contains(RequiredSkillsScorer.DEGRADED_NOTE);
    }

    @Test
    void keywordAndSemanticCombine_intoOneMergedScore() {
        // Only the keyword misses are sent for embedding.
        when(intelligenceClient.semanticSimilarity(anyList(), anyList(), anyDouble()))
                .thenReturn(semanticResult(List.of("Kubernetes", "Terraform"), List.of("Kubernetes")));

        ScoringContext context = context(
                jd().requiredSkills(json(List.of("Java", "Kubernetes", "Terraform", "Kafka"))).build(),
                chunk(SectionLabel.SKILLS, "Java, Kafka"),
                chunk(SectionLabel.EXPERIENCE, "Ran container orchestration for a payments platform"));

        SubScoreResult result = scorer.score(context);

        // 2 keyword (Java, Kafka) + 1 semantic (Kubernetes) of 4.
        assertThat(result.rawScore()).isEqualTo(75.0);
        assertThat(result.matched()).containsExactlyInAnyOrder("Java", "Kafka", "Kubernetes");
        assertThat(result.missing()).containsExactly("Terraform");
        assertThat(result.explanation())
                .isEqualTo("3 of 4 required skills matched (1 via semantic)");

        // Only the unmatched skills crossed the service boundary — the whole point
        // of running the cheap pass first.
        ArgumentCaptor<List<String>> phrases = ArgumentCaptor.captor();
        verify(intelligenceClient).semanticSimilarity(
                phrases.capture(), anyList(), anyDouble());
        assertThat(phrases.getValue()).containsExactly("Kubernetes", "Terraform");
    }

    @Test
    void allSkillsFoundByKeyword_skipsSemanticCallEntirely() {
        ScoringContext context = context(
                jd().requiredSkills(json(List.of("Java", "Kafka"))).build(),
                chunk(SectionLabel.SKILLS, "Java, Kafka"));

        SubScoreResult result = scorer.score(context);

        assertThat(result.rawScore()).isEqualTo(100.0);
        verify(intelligenceClient, never()).semanticSimilarity(anyList(), anyList(), anyDouble());
    }

    @Test
    void emptySkillsList_scoresHundredWithoutCallingService() {
        ScoringContext context = context(
                jd().requiredSkills(json(List.of())).build(),
                chunk(SectionLabel.SKILLS, "Java"));

        SubScoreResult result = scorer.score(context);

        assertThat(result.rawScore()).isEqualTo(100.0);
        assertThat(result.explanation()).isEqualTo("No required skills specified");
        verify(intelligenceClient, never()).semanticSimilarity(anyList(), anyList(), anyDouble());
    }
}
