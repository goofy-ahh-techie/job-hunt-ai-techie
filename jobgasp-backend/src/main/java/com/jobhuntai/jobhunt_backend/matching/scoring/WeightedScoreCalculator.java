package com.jobhuntai.jobhunt_backend.matching.scoring;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Combines the six sub-scores into one 0–100 overall score.
 *
 * <p>Separate from {@link SubScoreOrchestrator} on purpose: running the scorers and
 * weighting their output are different decisions that change for different reasons.
 * Re-weighting the model should not touch the code that executes it.
 *
 * <p>The result is rounded through {@link BigDecimal} rather than by multiplying and
 * dividing doubles, so the number stored is exactly the number reported — the column
 * is {@code NUMERIC(5,2)} and a value that cannot be represented at two decimal
 * places would be rounded a second time on the way into the database.
 */
@Component
public class WeightedScoreCalculator {

    private final ScoreWeights weights;

    public WeightedScoreCalculator() {
        this(ScoreWeights.DEFAULT);
    }

    public WeightedScoreCalculator(ScoreWeights weights) {
        this.weights = weights;
    }

    /** The overall score, 0–100, at two decimal places. */
    public double calculate(OrchestratedScores scores) {
        return calculateExact(scores).doubleValue();
    }

    /** As {@link #calculate}, as the {@code BigDecimal} the entity column stores. */
    public BigDecimal calculateExact(OrchestratedScores scores) {
        double weighted =
                scores.mustHave().rawScore() * weights.mustHave()
                        + scores.requiredSkills().rawScore() * weights.requiredSkills()
                        + scores.responsibilities().rawScore() * weights.responsibilities()
                        + scores.experience().rawScore() * weights.experience()
                        + scores.qualifications().rawScore() * weights.qualifications()
                        + scores.preferredSkills().rawScore() * weights.preferredSkills();

        return round(weighted);
    }

    /** Round any raw 0–100 score to the two decimal places the schema stores. */
    public static BigDecimal round(double rawScore) {
        return BigDecimal.valueOf(rawScore).setScale(2, RoundingMode.HALF_UP);
    }
}
