package com.jobhuntai.jobhunt_backend.common.exception;

/**
 * Raised when the intelligence-python service cannot be reached, times out, or
 * reports its own LLM backend as unavailable (HTTP 503). Mapped to HTTP 503 by the
 * global exception handler — the request is retryable once the service is healthy.
 */
public class IntelligenceServiceUnavailableException extends RuntimeException {

    public IntelligenceServiceUnavailableException(String message) {
        super(message);
    }

    public IntelligenceServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
