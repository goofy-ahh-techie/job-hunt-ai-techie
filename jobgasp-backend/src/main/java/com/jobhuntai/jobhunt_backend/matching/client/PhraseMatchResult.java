package com.jobhuntai.jobhunt_backend.matching.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * One phrase's semantic verdict as returned by the intelligence-python service.
 * Mirrors the Python {@code PhraseMatchResult} schema.
 *
 * <p>{@code @JsonNaming(SnakeCaseStrategy)} maps the service's snake_case JSON
 * onto these camelCase components, matching {@code JdExtractionResult}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PhraseMatchResult(
        String phrase,
        boolean matched,
        double bestScore,
        String bestMatchExcerpt
) {
}
