package com.tasklean.api.domain.auditlog;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Centralized {@code audit_log.entity_type} string constants (uppercase, matching the
 * audit trail's existing convention). Reference these instead of inlining strings.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuditEntityType {

    public static final String USER = "USER";
    public static final String TASK = "TASK";
    public static final String GROUP = "GROUP";
    public static final String GROUP_MEMBER = "GROUP_MEMBER";
    public static final String CATEGORY = "CATEGORY";
    public static final String TAG = "TAG";
    public static final String TASK_COMPLETION = "TASK_COMPLETION";
}
