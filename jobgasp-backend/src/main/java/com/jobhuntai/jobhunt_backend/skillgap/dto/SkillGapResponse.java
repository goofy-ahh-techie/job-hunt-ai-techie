package com.jobhuntai.jobhunt_backend.skillgap.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The full skill gap analysis for one match: every gap ranked most-urgent-first,
 * plus the two derived shortlists.
 *
 * <p>{@code quickWins} and {@code dealBreakers} are deliberately not just filters a
 * client could compute — {@code quickWins} is arithmetic on the estimates, but
 * {@code dealBreakers} is a judgement about whether the resume shows adjacent
 * experience, which only the analysis knows.
 *
 * <p>{@code overallScoreContext} is the match score at the time of analysis, not the
 * current one: it is what the reasoning was done against.
 */
public record SkillGapResponse(
        UUID id,
        UUID matchResultId,
        UUID resumeId,
        UUID jobDescriptionId,
        String gapSummary,
        List<GapItemResponse> gaps,
        List<String> quickWins,
        List<String> dealBreakers,
        BigDecimal overallScoreContext,
        String status,
        Instant lastAnalyzedAt,
        Instant createdAt
) {
}
