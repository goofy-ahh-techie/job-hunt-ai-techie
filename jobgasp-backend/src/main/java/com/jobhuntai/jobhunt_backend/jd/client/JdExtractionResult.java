package com.jobhuntai.jobhunt_backend.jd.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * The structured JD extraction as returned by the intelligence-python service.
 * Mirrors the Python {@code JdExtractionResult} schema.
 *
 * <p>{@code @JsonNaming(SnakeCaseStrategy)} maps the service's snake_case JSON
 * ({@code job_title}, {@code required_skills}) onto these camelCase components, so no
 * per-field {@code @JsonProperty} is needed. {@code employmentType} stays a
 * {@code String} here — the value is validated to the enum's names (or null) on the
 * Python side; conversion to {@code EmploymentType} happens in the service.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record JdExtractionResult(
        String jobTitle,
        String companyName,
        String companyDescription,
        String location,
        String employmentType,
        Integer experienceYearsMin,
        Integer experienceYearsMax,
        List<String> responsibilities,
        List<String> requiredSkills,
        List<String> preferredSkills,
        List<String> qualifications,
        List<String> mustHave,
        List<String> niceToHave,
        List<String> benefits,
        String rawSummary
) {
}
