package com.jobhuntai.jobhunt_backend.resume.domain;

/**
 * Status of text extraction for a single {@link ResumeVersion}.
 * Persisted as a string — never ordinal.
 */
public enum ExtractionStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
