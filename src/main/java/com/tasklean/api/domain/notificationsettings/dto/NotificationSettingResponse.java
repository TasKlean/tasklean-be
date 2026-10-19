package com.tasklean.api.domain.notificationsettings.dto;

import com.tasklean.api.domain.notification.NotificationType;
import com.tasklean.api.domain.notificationsettings.NotificationSettings;

/**
 * One notification type's delivery channels for the caller.
 *
 * @param type  the notification type
 * @param inApp whether it appears in the in-app notification list
 * @param email whether it is emailed
 */
public record NotificationSettingResponse(NotificationType type, boolean inApp, boolean email) {

    public static NotificationSettingResponse from(NotificationSettings settings) {
        return new NotificationSettingResponse(settings.getType(), settings.getInApp(), settings.getEmail());
    }

    /** What a type reads as before the user has saved anything for it (nothing is persisted). */
    public static NotificationSettingResponse defaults(NotificationType type) {
        return new NotificationSettingResponse(type,
                NotificationSettings.DEFAULT_IN_APP, NotificationSettings.DEFAULT_EMAIL);
    }
}
