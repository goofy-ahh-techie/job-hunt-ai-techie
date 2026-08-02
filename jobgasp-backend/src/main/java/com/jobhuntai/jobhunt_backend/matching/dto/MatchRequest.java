package com.jobhuntai.jobhunt_backend.matching.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to score one resume against one job description.
 *
 * <p>No {@code userId}: ownership comes from the security principal, so a caller
 * cannot ask for a match on somebody else's resume by putting their id in the body.
 * Both referenced records are ownership-checked before any scoring happens.
 */
public record MatchRequest(
        @NotNull(message = "resumeId is required")
        UUID resumeId,

        @NotNull(message = "jdId is required")
        UUID jdId
) {
}
