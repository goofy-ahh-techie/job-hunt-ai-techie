package com.jobhuntai.jobhunt_backend.resume.dto;

import com.jobhuntai.jobhunt_backend.resume.domain.FileType;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeStatus;

import java.time.Instant;
import java.util.UUID;

public record ResumeResponse(
        UUID id,
        UUID userId,
        String title,
        String fileName,
        FileType fileType,
        Long fileSizeBytes,
        ResumeStatus status,
        Instant createdAt
) {
}
