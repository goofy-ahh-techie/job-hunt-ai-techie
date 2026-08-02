package com.jobhuntai.jobhunt_backend.jd.domain;

/**
 * Lifecycle of a {@link JobDescription} from intake through extraction.
 * Persisted as a string via {@code @Enumerated(EnumType.STRING)} — never ordinal,
 * so reordering these constants can never corrupt existing rows.
 *
 * <p>{@code PROCESSING} is reserved for a future async extraction path; the current
 * synchronous pipeline moves a row straight from {@code UPLOADED} to {@code EXTRACTED}
 * or {@code FAILED}.
 */
public enum JdStatus {
    UPLOADED,
    PROCESSING,
    EXTRACTED,
    FAILED
}
