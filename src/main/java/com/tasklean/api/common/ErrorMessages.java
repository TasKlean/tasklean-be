package com.tasklean.api.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Centralized user-facing error message constants — "not found" strings, the generic platform
 * messages, and the auth-failure messages that more than one service throws. Reference these
 * instead of inlining strings.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorMessages {

    public static final String USER_NOT_FOUND = "User not found";
    public static final String GROUP_NOT_FOUND = "Group not found";
    public static final String INVALID_INVITE_CODE = "Invalid invite code";
    public static final String ALREADY_GROUP_MEMBER = "You are already a member of this group";
    public static final String GROUP_MEMBER_NOT_FOUND = "Group member not found";
    public static final String TASK_NOT_FOUND = "Task not found";
    public static final String CATEGORY_NOT_FOUND = "Category not found";
    public static final String TAG_NOT_FOUND = "Tag not found";
    public static final String DEVICE_NOT_FOUND = "Device not found";
    public static final String NOTIFICATION_NOT_FOUND = "Notification not found";
    public static final String ASSIGNED_MEMBER_NOT_FOUND = "Assigned member not found";
    public static final String ENDPOINT_NOT_FOUND = "Endpoint not found";
    public static final String METHOD_NOT_ALLOWED = "HTTP method not allowed for this endpoint";
    public static final String AUTHENTICATION_REQUIRED = "Authentication required";
    public static final String ACCESS_DENIED = "You do not have permission to perform this action";
    public static final String RATE_LIMITED = "Too many requests, please try again later";
    public static final String MALFORMED_REQUEST = "Malformed or invalid request body";
    public static final String INTERNAL_ERROR = "An unexpected error occurred";

    // Auth failures thrown from more than one service. They are centralized because the copies
    // must stay identical: each is the human half of an ErrorCodes value.
    public static final String INVALID_CREDENTIALS = "Invalid email or password";
    public static final String ACCOUNT_DEACTIVATED = "Account is deactivated";
    public static final String PASSWORD_INCORRECT = "Current password is incorrect";
    public static final String INVALID_EMAIL_OR_CODE = "Invalid email or code";
    public static final String INVALID_GOOGLE_TOKEN = "Invalid Google token";
    public static final String GOOGLE_EMAIL_UNVERIFIED = "Google account email is not verified";
    public static final String INVALID_RESET_TOKEN = "Invalid or expired reset token";

    // One message for an unknown and an already-revoked token alike, so a caller learns nothing
    // about a token it does not hold.
    public static final String INVALID_REFRESH_TOKEN = "Invalid refresh token";

    public static final String TWO_FACTOR_CODE_INVALID =
            "That code is not valid. Use a current code from your app or a recovery code.";

    // One message for every unusable challenge (unknown, expired, already spent, factor removed),
    // so a caller learns nothing about which it was.
    public static final String TWO_FACTOR_CHALLENGE_INVALID =
            "This sign-in request is no longer valid. Please sign in again.";
}
