package com.jobhuntai.jobhunt_backend.matching.scoring;

/**
 * The six sub-score results from one scoring run, in weight order.
 *
 * <p>A record of six named components rather than a {@code Map} or {@code List}:
 * the set of dimensions is fixed by an architectural decision, so the compiler
 * should be the thing that notices if one goes missing, and
 * {@link WeightedScoreCalculator} should not have to defend against a lookup
 * returning null.
 */
public record OrchestratedScores(
        SubScoreResult mustHave,
        SubScoreResult requiredSkills,
        SubScoreResult responsibilities,
        SubScoreResult experience,
        SubScoreResult qualifications,
        SubScoreResult preferredSkills
) {
}
