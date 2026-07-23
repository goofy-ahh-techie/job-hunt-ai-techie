package com.jobhuntai.jobhunt_backend.common.exception;

/**
 * Raised when a resume was stored successfully but could not be fully processed
 * (extraction, versioning, or chunking failed). The resume is recorded with status
 * FAILED before this propagates. Mapped to HTTP 422 by the global exception handler.
 */
public class ResumeProcessingException extends RuntimeException {

    public ResumeProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
