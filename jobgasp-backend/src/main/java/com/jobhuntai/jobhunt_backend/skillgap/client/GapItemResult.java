package com.jobhuntai.jobhunt_backend.skillgap.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * One gap as returned by the intelligence service. Mirrors the Python {@code GapItem}.
 *
 * <p>{@code priority} stays a {@code String} at the boundary — the value is already
 * constrained to the four enum names on the Python side, and conversion to
 * {@link com.jobhuntai.jobhunt_backend.skillgap.domain.GapPriority} happens in the
 * service. Same split as {@code JdExtractionResult.employmentType} in Phase 3.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GapItemResult(
        String skill,
        String priority,
        String reason,
        String learningRecommendation,
        Integer estimatedWeeks
) {
}
