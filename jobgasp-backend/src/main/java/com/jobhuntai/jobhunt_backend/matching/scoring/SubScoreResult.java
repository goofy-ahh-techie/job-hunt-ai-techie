package com.jobhuntai.jobhunt_backend.matching.scoring;

import java.util.List;

/**
 * One scorer's verdict: a 0–100 number, the JD items behind it, and a sentence
 * saying why.
 *
 * <p>The lists are the product, not decoration. A user cannot act on "required
 * skills: 62.0" — they can act on the four skills in {@code missing}. Every
 * scorer therefore reports its evidence, even the ones (experience) for which
 * the lists are empty because the dimension is numeric rather than itemised.
 */
public record SubScoreResult(
        double rawScore,
        List<String> matched,
        List<String> missing,
        String explanation
) {

    public SubScoreResult {
        matched = matched == null ? List.of() : List.copyOf(matched);
        missing = missing == null ? List.of() : List.copyOf(missing);
    }

    /** A score with itemised evidence on both sides. */
    public static SubScoreResult of(double rawScore, List<String> matched,
                                    List<String> missing, String explanation) {
        return new SubScoreResult(rawScore, matched, missing, explanation);
    }

    /** A score with no itemisable evidence — numeric dimensions, empty JD lists. */
    public static SubScoreResult scoreOnly(double rawScore, String explanation) {
        return new SubScoreResult(rawScore, List.of(), List.of(), explanation);
    }

    /**
     * The result recorded when a scorer throws. Zero rather than a skip: the
     * weighted total must stay a comparable 0–100 across matches, and silently
     * redistributing a failed dimension's weight would inflate the score of a
     * match that was measured less thoroughly.
     */
    public static SubScoreResult failed(String message) {
        return new SubScoreResult(0.0, List.of(), List.of(), "Scorer failed: " + message);
    }
}
