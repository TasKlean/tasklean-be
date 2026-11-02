package com.tasklean.api.common.exception;

/**
 * Thrown when a request is refused for a reason Bean Validation cannot express — a field that is
 * well-formed on its own but wrong against stored state (e.g. attaching an upload whose object
 * never arrived in the bucket). Mapped to HTTP 400 by {@link GlobalExceptionHandler}.
 *
 * <p>Distinct from {@link BusinessRuleException}: that one means a domain invariant would break
 * and is a 409, while this one means the caller sent something unusable.
 */
public class InvalidRequestException extends RuntimeException {

    private final String code;

    /**
     * @param message the detail message describing what is wrong with the request
     */
    public InvalidRequestException(String message) {
        this(message, null);
    }

    /**
     * @param message the detail message describing what is wrong with the request
     * @param code    a machine-readable {@code ErrorCodes} value, or {@code null} for none
     */
    public InvalidRequestException(String message, String code) {
        super(message);
        this.code = code;
    }

    /**
     * Returns the machine-readable code a client can branch on.
     *
     * @return the code, or {@code null} if this failure needs none
     */
    public String getCode() {
        return code;
    }
}
