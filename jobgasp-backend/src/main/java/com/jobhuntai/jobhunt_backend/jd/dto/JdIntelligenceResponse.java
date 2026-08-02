package com.jobhuntai.jobhunt_backend.jd.dto;

import com.jobhuntai.jobhunt_backend.jd.domain.EmploymentType;
import com.jobhuntai.jobhunt_backend.jd.domain.JdExtractionStatus;

import java.util.List;
import java.util.UUID;

/**
 * The extracted intelligence for a job description. The list fields are stored in the
 * database as JSON-serialised TEXT and deserialised back to {@code List<String>} by
 * {@code JdMapper}. {@code extractionStatus} and {@code extractionError} let a client
 * see a FAILED extraction and its reason through the API.
 */
public record JdIntelligenceResponse(
        UUID id,
        UUID jobDescriptionId,
        String jobTitle,
        String companyName,
        String companyDescription,
        String location,
        EmploymentType employmentType,
        Integer experienceYearsMin,
        Integer experienceYearsMax,
        List<String> responsibilities,
        List<String> requiredSkills,
        List<String> preferredSkills,
        List<String> qualifications,
        List<String> mustHave,
        List<String> niceToHave,
        List<String> benefits,
        String rawSummary,
        JdExtractionStatus extractionStatus,
        String extractionError
) {
}
