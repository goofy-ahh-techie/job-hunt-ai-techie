package com.jobhuntai.jobhunt_backend.jd.dto;

import com.jobhuntai.jobhunt_backend.jd.domain.JdSourceType;
import com.jobhuntai.jobhunt_backend.jd.domain.JdStatus;

import java.time.Instant;
import java.util.UUID;

/** Summary view of a job description (no extracted intelligence). */
public record JdResponse(
        UUID id,
        UUID userId,
        String title,
        JdSourceType sourceType,
        JdStatus status,
        Instant createdAt
) {
}
