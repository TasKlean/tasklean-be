package com.tasklean.api.common.exception;

/**
 * Thrown when an email the flow depends on could not be handed to the mail provider. Surfaces as a
 * 503 so the caller can retry, rather than the generic 500 an unhandled mail error would produce.
 */
public class EmailDeliveryException extends RuntimeException {

    /**
     * @param message the user-facing message to return
     * @param cause   the mail provider failure that caused it
     */
    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
