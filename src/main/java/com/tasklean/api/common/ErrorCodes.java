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

    /** The image never arrived in the bucket as declared; upload it again before attaching. */
    public static final String UPLOAD_NOT_USABLE = "UPLOAD_NOT_USABLE";

    /** The account is deactivated, so no retry of any kind will succeed. */
    public static final String ACCOUNT_DEACTIVATED = "ACCOUNT_DEACTIVATED";

    /** The page cursor was not one we handed out; ask for the list again without one. */
    public static final String PAGE_CURSOR_INVALID = "PAGE_CURSOR_INVALID";

    /** The round is not due yet; nothing to record until it comes round. */
    public static final String TASK_ROUND_NOT_DUE = "TASK_ROUND_NOT_DUE";

    /** Somebody already has the chore; hand it on instead of claiming it. */
    public static final String TASK_ALREADY_ASSIGNED = "TASK_ALREADY_ASSIGNED";

    /** The chore is archived; nothing can be recorded against it until it is brought back. */
    public static final String TASK_ARCHIVED = "TASK_ARCHIVED";

    /** This round of the chore is already in its history; re-read the task before acting. */
    public static final String TASK_INSTANCE_ALREADY_RECORDED = "TASK_INSTANCE_ALREADY_RECORDED";

    /** Only the newest entry in a chore's history can be undone, and this one is not it. */
    public static final String TASK_COMPLETION_NOT_LATEST = "TASK_COMPLETION_NOT_LATEST";

    private ErrorCodes() {
    }
}
