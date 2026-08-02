package com.jobhuntai.jobhunt_backend.matching.dto;

import com.jobhuntai.jobhunt_backend.matching.domain.MatchStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The full match result: overall score plus all six dimensions broken out.
 *
 * <p>The six sub-scores are separate fields rather than a map or a single number —
 * componentised scoring is a locked architectural decision, and the response shape is
 * where that decision becomes visible to a client.
 *
 * <p>{@code overallScore} is a {@code BigDecimal} so the value a client renders is
 * exactly the value stored in the {@code NUMERIC(5,2)} column; the sub-scores are
 * doubles because they are derived figures, not persisted-precision ones.
 */
public record MatchResponse(
        UUID id,
        UUID userId,
        UUID resumeId,
        UUID resumeVersionId,
        UUID jobDescriptionId,
        BigDecimal overallScore,
        SubScoreDetail mustHave,
        SubScoreDetail requiredSkills,
        SubScoreDetail responsibilities,
        SubScoreDetail experience,
        SubScoreDetail qualifications,
        SubScoreDetail preferredSkills,
        String scoreExplanation,
        MatchStatus status,
        Instant lastCalculatedAt,
        Instant createdAt
) {
}
