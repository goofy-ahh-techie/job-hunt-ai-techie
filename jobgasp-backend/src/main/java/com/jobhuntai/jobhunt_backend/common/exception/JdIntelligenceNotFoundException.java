package com.jobhuntai.jobhunt_backend.common.exception;

/**
 * Raised when a job description exists and is owned by the caller, but its extracted
 * intelligence has not been produced (or the extraction failed without a row). Mapped
 * to HTTP 404 by the global exception handler.
 */
public class JdIntelligenceNotFoundException extends RuntimeException {

    public JdIntelligenceNotFoundException(String message) {
        super(message);
    }
}
