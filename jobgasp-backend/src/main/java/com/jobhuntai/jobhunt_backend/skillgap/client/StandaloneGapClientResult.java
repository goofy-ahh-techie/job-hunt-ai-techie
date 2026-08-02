package com.jobhuntai.jobhunt_backend.skillgap.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * The resume-only gap assessment from intelligence-python. Mirrors the Python
 * {@code StandaloneGapResult}. Never persisted — this is computed on demand and
 * returned straight through, since it depends on nothing but the resume and would
 * go stale the moment that changed.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record StandaloneGapClientResult(
        List<String> strongDomains,
        List<String> weakDomains,
        List<String> recommendedAdditions,
        String generalAssessment
) {

    public StandaloneGapClientResult {
        strongDomains = strongDomains == null ? List.of() : List.copyOf(strongDomains);
        weakDomains = weakDomains == null ? List.of() : List.copyOf(weakDomains);
        recommendedAdditions =
                recommendedAdditions == null ? List.of() : List.copyOf(recommendedAdditions);
    }
}
