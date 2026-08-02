package com.jobhuntai.jobhunt_backend.matching.domain;

/**
 * Lifecycle of a {@code MatchResult} row. Persisted as a string — never ordinal.
 *
 * <p>{@code CALCULATING} is written before scoring starts and is not merely
 * decorative: scoring makes a slow remote call, so a row parked in this state is
 * how an interrupted run (JVM death mid-calculation) stays distinguishable from
 * one that never began.
 */
public enum MatchStatus {
    PENDING,
    CALCULATING,
    COMPLETED,
    FAILED
}
