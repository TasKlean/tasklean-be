package com.tasklean.api.common.exception;

import com.tasklean.api.common.ErrorCodes;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * Thrown on login when the account exists but its email has not been verified. A coded
 * {@link BadCredentialsException}, so it stays a 401 auth failure carrying
 * {@link ErrorCodes#EMAIL_NOT_VERIFIED} - a named type because the login flow and its tests both
 * single this case out.
 */
public class EmailNotVerifiedException extends AuthFailureException {

    public EmailNotVerifiedException(String message) {
        super(message, ErrorCodes.EMAIL_NOT_VERIFIED);
    }
}
