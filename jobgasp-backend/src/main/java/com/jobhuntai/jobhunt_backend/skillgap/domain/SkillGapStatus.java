package com.jobhuntai.jobhunt_backend.skillgap.domain;

/**
 * Lifecycle of a {@code skill_gap} row. Persisted as a string — never ordinal.
 *
 * <p>{@code ANALYZING} is written before the LLM call starts, so a run that dies
 * mid-flight stays distinguishable from one that never began. {@code FAILED}
 * carries {@code analysis_error} and is what makes an intelligence-service outage
 * visible through the API rather than only in the logs.
 */
public enum SkillGapStatus {
    PENDING,
    ANALYZING,
    COMPLETED,
    FAILED
}
