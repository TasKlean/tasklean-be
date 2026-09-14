package com.tasklean.api.common.exception;

/**
 * Thrown when an operation would violate a domain invariant (e.g. removing a group's last admin).
 * Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class BusinessRuleException extends RuntimeException {

    /**
     * @param message the detail message describing the violated rule
     */
    public BusinessRuleException(String message) {
        super(message);
    }
}
