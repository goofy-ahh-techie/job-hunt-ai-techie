package com.jobhuntai.jobhunt_backend.common.exception;

/**
 * Raised when the intelligence service reached the model but could not produce a
 * usable gap analysis (HTTP 422), or answered 2xx with an unsuccessful envelope.
 * Mapped to HTTP 422 by the global exception handler.
 *
 * <p>Unlike the Phase 4 semantic failures, this one is not degraded around: a gap
 * analysis with no analysis is nothing at all, so the caller records FAILED and
 * propagates rather than returning an empty result that reads like "no gaps".
 */
public class GapAnalysisFailedException extends RuntimeException {

    public GapAnalysisFailedException(String message) {
        super(message);
    }

    public GapAnalysisFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
