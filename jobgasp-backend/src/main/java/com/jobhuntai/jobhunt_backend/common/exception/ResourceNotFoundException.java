package com.jobhuntai.jobhunt_backend.common.exception;

/**
 * Raised when a requested resource does not exist, or exists but is not owned by
 * the requesting user. Mapped to HTTP 404 by the global exception handler.
 *
 * <p>Deliberately does not distinguish "missing" from "not yours" — both return 404
 * so the API never reveals that another user's resource exists.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
