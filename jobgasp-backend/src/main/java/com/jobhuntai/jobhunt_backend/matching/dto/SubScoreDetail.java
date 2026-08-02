package com.jobhuntai.jobhunt_backend.matching.dto;

import java.util.List;

/**
 * One dimension of a match as the API reports it: the number, the evidence behind
 * it, and the sentence explaining it.
 *
 * <p>The {@code missing} list is the part clients actually build on — it is the
 * candidate's gap for this dimension, and the input to the skill-gap phase.
 */
public record SubScoreDetail(
        double score,
        List<String> matched,
        List<String> missing,
        String explanation
) {
}
