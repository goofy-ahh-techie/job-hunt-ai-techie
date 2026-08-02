package com.jobhuntai.jobhunt_backend.skillgap.dto;

import java.util.List;
import java.util.UUID;

/**
 * A resume assessed on its own, with no target role. Never persisted — it depends on
 * nothing but the resume, and would go stale the moment that changed.
 */
public record StandaloneGapResponse(
        UUID resumeId,
        List<String> strongDomains,
        List<String> weakDomains,
        List<String> recommendedAdditions,
        String generalAssessment
) {
}
