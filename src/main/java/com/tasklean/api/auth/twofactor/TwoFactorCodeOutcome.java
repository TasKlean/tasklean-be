package com.tasklean.api.auth.twofactor;

/**
 * How a submitted second-factor code was resolved. The caller needs the distinction because a
 * recovery code is spent by using it, while an authenticator code is not.
 */
public enum TwoFactorCodeOutcome {

    /** Matched the authenticator secret within the allowed clock skew. */
    TOTP,

    /** Matched an unused recovery code, which is now consumed. */
    RECOVERY,

    /** No match - a wrong code, a replayed one, or an already-used recovery code. */
    INVALID
}
