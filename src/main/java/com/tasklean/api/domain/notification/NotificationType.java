package com.tasklean.api.domain.notification;

/**
 * The kinds of notification the app raises. Also the vocabulary users configure per channel in
 * {@code notification_settings}. Stored as the enum name in {@code notification.type}, which is
 * still a free-form column for historical rows.
 */
public enum NotificationType {

    TASK_ASSIGNED,
    TASK_COMPLETED,
    TASK_REMINDER,
    MEMBER_JOINED
}
