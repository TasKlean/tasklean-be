package com.tasklean.api.common.exception;

/**
 * Thrown when creating a resource that conflicts with an existing one (e.g. a duplicate
 * name or email). Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class DuplicateResourceException extends RuntimeException {

    /**
     * @param message the detail message describing the conflict
     */
    public DuplicateResourceException(String message) {
        super(message);
    }
}
