package com.jobhuntai.jobhunt_backend.jd.domain;

/**
 * State of the AI extraction for a {@link JdIntelligence} row.
 * Persisted as a string via {@code @Enumerated(EnumType.STRING)}.
 *
 * <p>{@code IN_PROGRESS} is reserved for a future async extraction path; the current
 * synchronous pipeline writes a row as {@code COMPLETED} on success, or {@code FAILED}
 * with {@code extraction_error} populated. {@code PENDING} is the column default.
 */
public enum JdExtractionStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
