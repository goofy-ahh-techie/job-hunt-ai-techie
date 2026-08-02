package com.jobhuntai.jobhunt_backend.matching.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * The semantic-similarity response from the intelligence-python service: a verdict
 * per source phrase plus the aggregate. Mirrors the Python
 * {@code SemanticSimilarityResponse} schema.
 *
 * <p>Unlike JD extraction this has no {@code success/data/error} envelope — semantic
 * matching either produces a full result set or fails with a status code, so there is
 * no partial-success shape for an envelope to carry.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SemanticSimilarityResult(
        List<PhraseMatchResult> results,
        int matchCount,
        double matchPercentage
) {

    public SemanticSimilarityResult {
        results = results == null ? List.of() : List.copyOf(results);
    }

    /** The phrases the service considered present in the target text. */
    public List<String> matchedPhrases() {
        return results.stream().filter(PhraseMatchResult::matched)
                .map(PhraseMatchResult::phrase).toList();
    }

    /** The phrases the service could not find — the reportable gap. */
    public List<String> unmatchedPhrases() {
        return results.stream().filter(result -> !result.matched())
                .map(PhraseMatchResult::phrase).toList();
    }
}
