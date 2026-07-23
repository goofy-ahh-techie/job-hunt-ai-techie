package com.jobhuntai.jobhunt_backend.common.exception;

/**
 * Raised when an uploaded file cannot be validated or persisted to storage.
 * Mapped to HTTP 500 by the global exception handler.
 */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message) {
        super(message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
