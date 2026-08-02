package com.jobhuntai.jobhunt_backend.matching.dto;

import com.jobhuntai.jobhunt_backend.matching.domain.MatchStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A match as it appears in a list — score and status, no breakdown.
 *
 * <p>Separate from {@link MatchResponse} rather than a nulled-out version of it: a
 * list of twenty matches does not need twenty sub-score breakdowns and their matched
 * and missing arrays, and a client reading this shape knows the detail is a fetch
 * away rather than absent.
 */
public record MatchSummaryResponse(
        UUID id,
        UUID resumeId,
        UUID jobDescriptionId,
        BigDecimal overallScore,
        MatchStatus status,
        Instant lastCalculatedAt
) {
}
