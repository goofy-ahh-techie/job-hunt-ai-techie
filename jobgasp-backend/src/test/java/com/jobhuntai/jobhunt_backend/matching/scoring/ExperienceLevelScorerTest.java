package com.jobhuntai.jobhunt_backend.matching.scoring;

import com.jobhuntai.jobhunt_backend.resume.domain.SectionLabel;
import org.junit.jupiter.api.Test;

import java.time.Year;

import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.chunk;
import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.context;
import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.jd;
import static org.assertj.core.api.Assertions.assertThat;

class ExperienceLevelScorerTest {

    private final ExperienceLevelScorer scorer = new ExperienceLevelScorer();

    /** 2015–2023 is an unambiguous 8-year span regardless of when the tests run. */
    private static final String EIGHT_YEAR_RESUME =
            "Senior Engineer, Acme 2019 - 2023\nEngineer, Initech 2015 - 2019";

    private SubScoreResult scoreWith(String experienceText, Integer min, Integer max) {
        return scorer.score(context(
                jd().experienceYearsMin(min).experienceYearsMax(max).build(),
                chunk(SectionLabel.EXPERIENCE, experienceText)));
    }

    @Test
    void atOrAboveMinimum_scoresHundred() {
        SubScoreResult result = scoreWith(EIGHT_YEAR_RESUME, 5, 10);

        assertThat(result.rawScore()).isEqualTo(100.0);
        assertThat(result.explanation())
                .isEqualTo("Resume shows ~8 years experience; JD requires 5–10 years");
    }

    @Test
    void oneYearShort_scoresSeventyFive() {
        assertThat(scoreWith(EIGHT_YEAR_RESUME, 9, 12).rawScore()).isEqualTo(75.0);
    }

    @Test
    void twoYearsShort_scoresForty() {
        assertThat(scoreWith(EIGHT_YEAR_RESUME, 10, 14).rawScore()).isEqualTo(40.0);
    }

    @Test
    void significantlyBelow_scoresTen() {
        assertThat(scoreWith(EIGHT_YEAR_RESUME, 12, 15).rawScore()).isEqualTo(10.0);
    }

    @Test
    void nullMinimum_scoresHundredWithoutInferring() {
        SubScoreResult result = scoreWith(EIGHT_YEAR_RESUME, null, 10);

        assertThat(result.rawScore()).isEqualTo(100.0);
        assertThat(result.explanation()).isEqualTo("No experience requirement specified");
    }

    @Test
    void nullMaximum_scoresHundredWithoutInferring() {
        // Note: llama3.2:3b frequently leaves experience_years_max null, so this
        // neutral path is the common one in practice, not an edge case.
        SubScoreResult result = scoreWith(EIGHT_YEAR_RESUME, 5, null);

        assertThat(result.rawScore()).isEqualTo(100.0);
        assertThat(result.explanation()).isEqualTo("No experience requirement specified");
    }

    @Test
    void parsesHyphenEnDashAndOngoingRoles() {
        int currentYear = Year.now().getValue();

        // Hyphen with spaces, en dash with "current", and an en-dash "present" —
        // all three appear in real resumes depending on the editor's autoformatting.
        assertThat(scoreWith("Engineer 2018 - 2022", 4, 6).explanation())
                .contains("~4 years");
        assertThat(scoreWith("Engineer 2020–current", 1, 2).explanation())
                .contains("~%d years".formatted(currentYear - 2020));
        assertThat(scoreWith("Engineer 2019 – present", 1, 2).explanation())
                .contains("~%d years".formatted(currentYear - 2019));
        assertThat(scoreWith("Engineer 2017—now", 1, 2).explanation())
                .contains("~%d years".formatted(currentYear - 2017));
    }

    @Test
    void spansEarliestStartToLatestEnd_ratherThanSummingOverlappingRoles() {
        // Two concurrent roles across the same window: summing would report 8 years
        // of experience for 4 years of career.
        SubScoreResult result = scoreWith(
                "Engineer, Acme 2018 - 2022\nContractor, Initech 2018 - 2022", 4, 6);

        assertThat(result.explanation()).contains("~4 years");
    }

    @Test
    void unparseableResume_infersZeroYearsRatherThanFailing() {
        SubScoreResult result = scoreWith("Engineer at Acme, several years", 5, 8);

        assertThat(result.rawScore()).isEqualTo(10.0);
        assertThat(result.explanation()).contains("~0 years");
    }

    @Test
    void reportsNoItemisedEvidence() {
        SubScoreResult result = scoreWith(EIGHT_YEAR_RESUME, 5, 10);

        // Experience is numeric: there is no list of matched/missing items to show.
        assertThat(result.matched()).isEmpty();
        assertThat(result.missing()).isEmpty();
    }
}
