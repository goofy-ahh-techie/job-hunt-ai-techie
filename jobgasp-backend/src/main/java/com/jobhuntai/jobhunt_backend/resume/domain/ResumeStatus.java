package com.jobhuntai.jobhunt_backend.resume.domain;

/**
 * Lifecycle of a {@link Resume} as it moves through upload and parsing.
 * Persisted as a string via {@code @Enumerated(EnumType.STRING)} — never ordinal,
 * so reordering these constants can never corrupt existing rows.
 */
public enum ResumeStatus {
    UPLOADED,
    PROCESSING,
    PARSED,
    FAILED
}
