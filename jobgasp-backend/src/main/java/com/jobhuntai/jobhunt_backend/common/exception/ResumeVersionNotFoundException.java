package com.jobhuntai.jobhunt_backend.common.exception;

/**
 * Raised when a resume exists and is owned by the caller, but has no extracted
 * version to score against. Mapped to HTTP 404 by the global exception handler.
 *
 * <p>Distinct from {@link ResourceNotFoundException} on purpose: "your resume was
 * never parsed" is a different problem with a different fix than "that resume does
 * not exist", and collapsing both into one message would send the user looking for a
 * missing id that is in fact right there.
 */
public class ResumeVersionNotFoundException extends RuntimeException {

    public ResumeVersionNotFoundException(String message) {
        super(message);
    }
}
