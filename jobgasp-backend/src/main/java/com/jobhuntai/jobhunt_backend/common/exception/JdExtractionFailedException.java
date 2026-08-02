package com.jobhuntai.jobhunt_backend.common.exception;

/**
 * Raised when the intelligence service was reached but could not produce a usable
 * extraction — the LLM reply failed to parse or failed validation (HTTP 422 from the
 * service). The JD is recorded with status FAILED and its intelligence row with
 * extraction_status FAILED before this propagates. Mapped to HTTP 422.
 */
public class JdExtractionFailedException extends RuntimeException {

    public JdExtractionFailedException(String message) {
        super(message);
    }

    public JdExtractionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
