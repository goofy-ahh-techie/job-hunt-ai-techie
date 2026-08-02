package com.jobhuntai.jobhunt_backend.skillgap.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * The gap analysis request body sent to intelligence-python. Mirrors the Python
 * {@code GapAnalysisRequest} schema; {@code @JsonNaming(SnakeCaseStrategy)} maps these
 * camelCase components onto its snake_case fields, as in every other cross-boundary
 * record since Phase 3.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GapAnalysisClientRequest(
        String jobTitle,
        String companyName,
        List<String> missingSkills,
        List<String> missingMustHaves,
        List<String> preferredMissing,
        String resumeTextSummary,
        double overallScore
) {
}
