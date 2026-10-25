package com.tasklean.api.common;

/**
 * Stable, machine-readable error codes returned in the {@code code} field of {@link ApiResponse}.
 * A client branches on these instead of matching a human message, which also lets it translate the
 * message itself. Only a failure a client must handle differently gets a code - where every failure
 * is deliberately indistinguishable (login), there is none.
 */
public final class ErrorCodes {

    /** The password was correct but the account's email is still unverified. */
    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";

    /** A password supplied to re-authenticate for a self-service change was missing or wrong. */
    public static final String PASSWORD_INVALID = "PASSWORD_INVALID";

    /** The submitted two-factor code did not match; the caller can try another one. */
    public static final String TWO_FACTOR_CODE_INVALID = "TWO_FACTOR_CODE_INVALID";

    /** The sign-in challenge is unusable (unknown, expired, spent, out of attempts); start over. */
    public static final String TWO_FACTOR_CHALLENGE_INVALID = "TWO_FACTOR_CHALLENGE_INVALID";

    /** An email the flow depends on could not be sent; the same request is worth retrying. */
    public static final String EMAIL_DELIVERY_FAILED = "EMAIL_DELIVERY_FAILED";

    /** The account is deactivated, so no retry of any kind will succeed. */
    public static final String ACCOUNT_DEACTIVATED = "ACCOUNT_DEACTIVATED";

    private ErrorCodes() {
    }
}
