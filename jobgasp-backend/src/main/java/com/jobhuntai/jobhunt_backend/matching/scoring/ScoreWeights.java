package com.jobhuntai.jobhunt_backend.matching.scoring;

/**
 * The relative weight of each sub-score in the overall match.
 *
 * <p>The distribution encodes what actually decides an application. Must-haves and
 * required skills together carry 55% because they are what a screen filters on;
 * preferred skills carry 5% because they break ties and nothing more.
 *
 * <p>Java records cannot declare per-component defaults, so the canonical set is the
 * {@link #DEFAULT} constant rather than inline initialisers. Making it a value object
 * at all — instead of six constants on the calculator — is what allows a future phase
 * to score against per-user or per-role weightings without touching the arithmetic.
 */
public record ScoreWeights(
        double mustHave,
        double requiredSkills,
        double responsibilities,
        double experience,
        double qualifications,
        double preferredSkills
) {

    /** The locked Phase 4 distribution: 30/25/20/12/8/5. */
    public static final ScoreWeights DEFAULT =
            new ScoreWeights(0.30, 0.25, 0.20, 0.12, 0.08, 0.05);

    public ScoreWeights {
        double sum = mustHave + requiredSkills + responsibilities
                + experience + qualifications + preferredSkills;
        // Weights that do not sum to 1 would silently rescale every score, making
        // matches from different builds incomparable. Tolerance covers binary
        // floating-point representation, not sloppy weights.
        if (Math.abs(sum - 1.0) > 1e-9) {
            throw new IllegalArgumentException(
                    "Score weights must sum to 1.0 but summed to " + sum);
        }
    }
}
