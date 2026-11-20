package com.tasklean.api.common.exception;

import lombok.Getter;

/**
 * Thrown when an operation would violate a domain invariant (e.g. removing a group's last admin).
 * Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
@Getter
public class BusinessRuleException extends RuntimeException {

    /**
     *
     * -- GETTER --
     *  Returns the machine-readable code a client can branch on.
     * <p>
     + @param message the detail message describing the violated rule
      * @return the code, or {@code null} if this conflict needs none
     */
    private final String code;

    /**
     * @param message the detail message describing which invariant would break
     */
    public BusinessRuleException(String message) {
        this(message, null);
    }

    /**
     * @param message the detail message describing which invariant would break
     * @param code    a machine-readable {@code ErrorCodes} value, or {@code null} for none
     */
    public BusinessRuleException(String message, String code) {
        super(message);
        this.code = code;
    }
}
