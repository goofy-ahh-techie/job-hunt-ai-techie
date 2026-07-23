package com.jobhuntai.jobhunt_backend.resume.domain;

/**
 * Section a {@link ResumeChunk} belongs to, assigned by the chunker.
 * {@code OTHER} is the fallback for unmatched or header-less text.
 * Persisted as a string — never ordinal.
 */
public enum SectionLabel {
    SUMMARY,
    EXPERIENCE,
    SKILLS,
    EDUCATION,
    OTHER
}
