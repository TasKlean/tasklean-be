package com.tasklean.api.domain.auditlog;

/**
 * The set of auditable actions recorded in the audit trail.
 * Stored as the enum name in {@code audit_log.action}.
 */
public enum AuditAction {

    // Group-scoped domain mutations
    CREATE,
    UPDATE,
    DELETE,
    COMPLETE,
    MEMBER_ADDED,
    MEMBER_REMOVED,
    ROLE_CHANGED,

    // Authentication / account events (subject is the user; no group context)
    REGISTER,
    ACCOUNT_DELETED,
    LOGIN,
    LOGOUT,
    LOGIN_FAILED,
    EMAIL_VERIFIED,
    PASSWORD_RESET,
    PASSWORD_CHANGED,
    PASSWORD_SET,
    GOOGLE_LINKED,
    GOOGLE_UNLINKED,
    NEW_DEVICE_LOGIN,
    SESSION_REVOKED,
    ALL_SESSIONS_REVOKED,
    DEVICE_REMOVED
}
