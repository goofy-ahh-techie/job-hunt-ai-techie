package com.jobhuntai.jobhunt_backend.resume.dto;

import com.jobhuntai.jobhunt_backend.resume.domain.ExtractionStatus;

import java.time.Instant;
import java.util.UUID;

public record ResumeVersionResponse(
        UUID id,
        UUID resumeId,
        Integer versionNumber,
        String label,
        Integer wordCount,
        Integer charCount,
        ExtractionStatus extractionStatus,
        Instant createdAt
) {
}
