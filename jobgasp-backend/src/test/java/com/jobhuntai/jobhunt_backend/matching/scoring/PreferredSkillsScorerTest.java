package com.jobhuntai.jobhunt_backend.matching.scoring;

import com.jobhuntai.jobhunt_backend.resume.domain.SectionLabel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.chunk;
import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.context;
import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.jd;
import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;

class PreferredSkillsScorerTest {

    private final PreferredSkillsScorer scorer = new PreferredSkillsScorer();

    @Test
    void emptyPreferredSkills_scoresZeroNotHundred() {
        ScoringContext context = context(
                jd().preferredSkills(json(List.of())).build(),
                chunk(SectionLabel.SKILLS, "Java, Kafka"));

        SubScoreResult result = scorer.score(context);

        // The inverse of every other scorer's empty case: no preferences listed
        // means no bonus available, not a perfectly satisfied requirement.
        assertThat(result.rawScore()).isEqualTo(0.0);
        assertThat(result.explanation()).isEqualTo("No preferred skills specified (no bonus)");
    }

    @Test
    void partialMatch_scoresProportionally() {
        ScoringContext context = context(
                jd().preferredSkills(json(List.of("Kafka", "GraphQL", "Rust", "Terraform"))).build(),
                chunk(SectionLabel.SKILLS, "Java, Kafka, Terraform"));

        SubScoreResult result = scorer.score(context);

        assertThat(result.rawScore()).isEqualTo(50.0);
        assertThat(result.matched()).containsExactly("Kafka", "Terraform");
        assertThat(result.explanation()).isEqualTo("2 of 4 preferred skills found (bonus)");
    }

    @Test
    void allPreferredFound_scoresHundred() {
        ScoringContext context = context(
                jd().preferredSkills(json(List.of("Kafka"))).build(),
                chunk(SectionLabel.SKILLS, "Kafka, Java"));

        assertThat(scorer.score(context).rawScore()).isEqualTo(100.0);
    }

    @Test
    void unmatchedPreferredSkillsAreNotReportedAsGaps() {
        ScoringContext context = context(
                jd().preferredSkills(json(List.of("Rust", "GraphQL"))).build(),
                chunk(SectionLabel.SKILLS, "Java"));

        SubScoreResult result = scorer.score(context);

        assertThat(result.rawScore()).isEqualTo(0.0);
        // A missed nice-to-have is not something to tell the user to go fix.
        assertThat(result.missing()).isEmpty();
    }
}
