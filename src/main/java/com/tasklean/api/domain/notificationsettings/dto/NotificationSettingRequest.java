package com.tasklean.api.domain.notificationsettings.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request body for one notification type's channels — a full replace of that entry, so both
 * channels are required. The type itself comes from the path, never the body.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSettingRequest {

    @NotNull(message = "In-app preference is required")
    private Boolean inApp;

    @NotNull(message = "Email preference is required")
    private Boolean email;
}
