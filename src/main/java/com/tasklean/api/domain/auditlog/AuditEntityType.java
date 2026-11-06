package com.tasklean.api.domain.auditlog;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Set;

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

    /**
     * Every type the trail can carry. The column is a free-form string, but a caller filtering by
     * one is checked against this set, so a typo is a 400 rather than a feed that is quietly empty.
     */
    public static final Set<String> ALL =
            Set.of(USER, TASK, GROUP, GROUP_MEMBER, CATEGORY, TAG, TASK_COMPLETION);
}
