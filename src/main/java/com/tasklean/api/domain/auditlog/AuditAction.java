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
    LOGIN_FAILED
}
