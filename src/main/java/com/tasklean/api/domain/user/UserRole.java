package com.tasklean.api.domain.user;

/**
 * Platform-level role governing a user's access across the whole application —
 * distinct from a user's per-group role ({@code GroupMember.role}).
 * Stored as the enum name in {@code user.role}.
 */
public enum UserRole {

    // Full platform access, including granting or revoking platform roles.
    SUPER_ADMIN,

    // Support access: may read data across the platform for troubleshooting,
    // but performs no destructive actions and cannot change roles.
    ADMIN,

    // Standard account (default): limited to the user's own data and the groups they belong to.
    USER
}
