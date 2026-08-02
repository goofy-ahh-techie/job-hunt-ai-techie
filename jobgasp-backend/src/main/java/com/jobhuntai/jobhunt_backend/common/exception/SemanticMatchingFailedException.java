package com.jobhuntai.jobhunt_backend.common.exception;

/**
 * Raised when the intelligence service rejected a semantic-similarity request (HTTP
 * 422) or answered with an unusable body. Mapped to HTTP 422 by the global handler.
 *
 * <p>In practice the scorers catch this alongside
 * {@link IntelligenceServiceUnavailableException} and fall back to keyword matching,
 * so it rarely reaches the handler — the mapping exists so that a caller using the
 * client directly gets a meaningful status rather than a 500.
 */
public class SemanticMatchingFailedException extends RuntimeException {

    public SemanticMatchingFailedException(String message) {
        super(message);
    }

    public SemanticMatchingFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
