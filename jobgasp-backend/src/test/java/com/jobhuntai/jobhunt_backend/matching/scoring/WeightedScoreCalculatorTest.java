package com.jobhuntai.jobhunt_backend.matching.scoring;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeightedScoreCalculatorTest {

    private final WeightedScoreCalculator calculator = new WeightedScoreCalculator();

    private static OrchestratedScores scores(double mustHave, double requiredSkills,
                                             double responsibilities, double experience,
                                             double qualifications, double preferredSkills) {
        return new OrchestratedScores(
                SubScoreResult.scoreOnly(mustHave, ""),
                SubScoreResult.scoreOnly(requiredSkills, ""),
                SubScoreResult.scoreOnly(responsibilities, ""),
                SubScoreResult.scoreOnly(experience, ""),
                SubScoreResult.scoreOnly(qualifications, ""),
                SubScoreResult.scoreOnly(preferredSkills, ""));
    }

    @Test
    void allPerfect_yieldsHundred() {
        assertThat(calculator.calculate(scores(100, 100, 100, 100, 100, 100)))
                .isEqualTo(100.0);
    }

    @Test
    void allZero_yieldsZero() {
        assertThat(calculator.calculate(scores(0, 0, 0, 0, 0, 0))).isEqualTo(0.0);
    }

    @Test
    void mixedScores_areWeightedToTwoDecimalPlaces() {
        // 90*0.30 + 80*0.25 + 65*0.20 + 40*0.12 + 100*0.08 + 60*0.05
        // = 27 + 20 + 13 + 4.8 + 8 + 3 = 75.80
        assertThat(calculator.calculate(scores(90, 80, 65, 40, 100, 60)))
                .isEqualTo(75.80);
    }

    @Test
    void exactValueIsReturnedAtTheSchemaScale() {
        BigDecimal exact = calculator.calculateExact(scores(90, 80, 65, 40, 100, 60));

        // Scale 2 matters: the column is NUMERIC(5,2), and a value that arrived at
        // some other scale would be rounded a second time on the way in.
        assertThat(exact).isEqualByComparingTo("75.80");
        assertThat(exact.scale()).isEqualTo(2);
    }

    @Test
    void eachDimensionContributesExactlyItsWeight() {
        // One dimension at 100 and the rest at 0 must produce that weight × 100.
        assertThat(calculator.calculate(scores(100, 0, 0, 0, 0, 0))).isEqualTo(30.0);
        assertThat(calculator.calculate(scores(0, 100, 0, 0, 0, 0))).isEqualTo(25.0);
        assertThat(calculator.calculate(scores(0, 0, 100, 0, 0, 0))).isEqualTo(20.0);
        assertThat(calculator.calculate(scores(0, 0, 0, 100, 0, 0))).isEqualTo(12.0);
        assertThat(calculator.calculate(scores(0, 0, 0, 0, 100, 0))).isEqualTo(8.0);
        assertThat(calculator.calculate(scores(0, 0, 0, 0, 0, 100))).isEqualTo(5.0);
    }

    @Test
    void roundingIsHalfUp() {
        // Sub-scores from a "2 of 3" style split are repeating decimals; the stored
        // value must be the one the API reports.
        double twoOfThree = 200.0 / 3;
        assertThat(calculator.calculate(scores(twoOfThree, 0, 0, 0, 0, 0)))
                .isEqualTo(20.0);
        assertThat(WeightedScoreCalculator.round(66.665)).isEqualByComparingTo("66.67");
    }

    @Test
    void weightsThatDoNotSumToOneAreRejected() {
        assertThatThrownBy(() -> new ScoreWeights(0.5, 0.5, 0.5, 0.5, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must sum to 1.0");
    }

    @Test
    void defaultWeightsMatchTheLockedDistribution() {
        assertThat(ScoreWeights.DEFAULT.mustHave()).isEqualTo(0.30);
        assertThat(ScoreWeights.DEFAULT.requiredSkills()).isEqualTo(0.25);
        assertThat(ScoreWeights.DEFAULT.responsibilities()).isEqualTo(0.20);
        assertThat(ScoreWeights.DEFAULT.experience()).isEqualTo(0.12);
        assertThat(ScoreWeights.DEFAULT.qualifications()).isEqualTo(0.08);
        assertThat(ScoreWeights.DEFAULT.preferredSkills()).isEqualTo(0.05);
    }
}
