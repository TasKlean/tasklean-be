package com.tasklean.api.common.exception;

/**
 * Thrown when a requested resource does not exist. Mapped to HTTP 404 by
 * {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * @param message the detail message describing what was not found
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
