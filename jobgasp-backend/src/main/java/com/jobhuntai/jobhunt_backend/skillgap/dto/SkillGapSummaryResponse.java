package com.jobhuntai.jobhunt_backend.skillgap.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A gap analysis as it appears in a list: the counts that matter for triage, not the
 * full breakdown.
 *
 * <p>Carries {@code dealBreakers} in full while reducing everything else to counts,
 * because a deal-breaker is the one thing a candidate scanning a list of roles needs
 * to see without opening each one.
 */
public record SkillGapSummaryResponse(
        UUID id,
        UUID matchResultId,
        UUID jobDescriptionId,
        int criticalGapCount,
        int highGapCount,
        List<String> dealBreakers,
        BigDecimal overallScoreContext,
        Instant lastAnalyzedAt
) {
}
