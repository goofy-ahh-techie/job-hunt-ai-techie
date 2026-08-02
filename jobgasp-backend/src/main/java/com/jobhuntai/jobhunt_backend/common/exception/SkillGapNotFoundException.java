package com.jobhuntai.jobhunt_backend.common.exception;

/**
 * Raised when a match exists and is owned by the caller, but no gap analysis has been
 * run for it yet. Mapped to HTTP 404 by the global exception handler.
 *
 * <p>Distinct from {@link ResourceNotFoundException} for the same reason as
 * {@code JdIntelligenceNotFoundException}: "you have not analysed this match yet" is a
 * different problem with a different fix than "that match does not exist", and
 * collapsing them would send the user hunting for an id that is in fact valid.
 */
public class SkillGapNotFoundException extends RuntimeException {

    public SkillGapNotFoundException(String message) {
        super(message);
    }
}
