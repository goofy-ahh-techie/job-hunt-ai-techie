package com.jobhuntai.jobhunt_backend.skillgap.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * The gap analysis payload from intelligence-python. Mirrors the Python
 * {@code GapAnalysisResult}.
 *
 * <p>{@code gaps} arrives already sorted CRITICAL → HIGH → MEDIUM → LOW, with
 * priorities corrected against their source lists and {@code quickWins} recomputed
 * from the estimates. That post-processing lives on the Python side so a second
 * consumer of the service gets the same guarantees; nothing here re-sorts.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GapAnalysisClientResult(
        String gapSummary,
        List<GapItemResult> gaps,
        List<String> quickWins,
        List<String> dealBreakers
) {

    public GapAnalysisClientResult {
        gaps = gaps == null ? List.of() : List.copyOf(gaps);
        quickWins = quickWins == null ? List.of() : List.copyOf(quickWins);
        dealBreakers = dealBreakers == null ? List.of() : List.copyOf(dealBreakers);
    }
}
