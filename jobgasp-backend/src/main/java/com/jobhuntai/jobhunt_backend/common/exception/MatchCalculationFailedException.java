package com.jobhuntai.jobhunt_backend.common.exception;

/**
 * Raised when match scoring fails for a reason the engine could not degrade around.
 * Mapped to HTTP 500 by the global exception handler.
 *
 * <p>Genuinely exceptional, not a routine outcome: a missing resume is a 404 and the
 * intelligence service being unreachable is absorbed by the scorers as a keyword-only
 * match. Reaching this exception means something broke that should not have, so the
 * match row is left recording {@code FAILED} to make it visible after the fact.
 */
public class MatchCalculationFailedException extends RuntimeException {

    public MatchCalculationFailedException(String message) {
        super(message);
    }

    public MatchCalculationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
