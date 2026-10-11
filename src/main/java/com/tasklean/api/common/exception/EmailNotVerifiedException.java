package com.tasklean.api.common.exception;

import org.springframework.security.authentication.BadCredentialsException;

/**
 * Thrown on login when the account exists but its email has not been verified. Extends
 * {@link BadCredentialsException} so it stays a 401 auth failure (and keeps the existing audit and
 * message), while carrying the machine-readable {@link #CODE}.
 */
public class EmailNotVerifiedException extends BadCredentialsException {

    public static final String CODE = "EMAIL_NOT_VERIFIED";

    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
