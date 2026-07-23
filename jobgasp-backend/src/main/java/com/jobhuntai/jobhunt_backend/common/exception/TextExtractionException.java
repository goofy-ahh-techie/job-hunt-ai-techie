package com.jobhuntai.jobhunt_backend.common.exception;

/**
 * Raised when text cannot be extracted from a stored resume file.
 * Mapped to HTTP 500 by the global exception handler.
 */
public class TextExtractionException extends RuntimeException {

    public TextExtractionException(String message) {
        super(message);
    }

    public TextExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
