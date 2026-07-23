package com.jobhuntai.jobhunt_backend.rawresume.dto;

import java.sql.Timestamp;
import java.util.UUID;

public record RawResumeResponse(
        UUID id,
        UUID userId,
        String fileName,
        String sourceType,
        String status,
        Timestamp uploadedAt,
        Timestamp updatedAt
) {
}
