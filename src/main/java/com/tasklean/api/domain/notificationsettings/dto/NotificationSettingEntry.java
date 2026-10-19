package com.tasklean.api.domain.notificationsettings.dto;

import com.tasklean.api.domain.notification.NotificationType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * One entry of a batch update — the same shape the GET returns, so a client can send back the
 * list it rendered. Unlike the single-type endpoint, the type travels in the body.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSettingEntry {

    @NotNull(message = "Notification type is required")
    private NotificationType type;

    @NotNull(message = "In-app preference is required")
    private Boolean inApp;

    @NotNull(message = "Email preference is required")
    private Boolean email;
}
