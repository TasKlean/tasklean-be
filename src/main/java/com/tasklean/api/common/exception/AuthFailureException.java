package com.tasklean.api.common.exception;

import com.tasklean.api.common.ErrorCodes;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * A 401 authentication failure that carries an {@link ErrorCodes} value, for the cases where the
 * client must react differently - retry the code, restart the sign-in, flag the password field.
 * Plain {@link BadCredentialsException} stays the right throw wherever the failures are
 * deliberately indistinguishable, as on login.
 */
public class AuthFailureException extends BadCredentialsException {

    private final String code;

    /**
     * @param message the human-readable failure message
     * @param code    the machine-readable code from {@link ErrorCodes}
     */
    public AuthFailureException(String message, String code) {
        super(message);
        this.code = code;
    }

    /**
     * @return the machine-readable code for the client to branch on
     */
    public String getCode() {
        return code;
    }
}
