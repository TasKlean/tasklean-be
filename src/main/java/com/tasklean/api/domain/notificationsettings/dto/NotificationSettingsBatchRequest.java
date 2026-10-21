package com.tasklean.api.domain.notificationsettings.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tasklean.api.domain.notification.NotificationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

/**
 * Request body for a batch update: each entry fully replaces that type's channels. Types the
 * batch omits are left untouched.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSettingsBatchRequest {

    @NotEmpty(message = "At least one notification setting is required")
    private List<@Valid NotificationSettingEntry> settings;

    // A repeated type would make the applied result depend on entry order, so the batch is
    // rejected instead of silently letting the last one win.
    @JsonIgnore
    @AssertTrue(message = "Each notification type may appear only once in a batch")
    public boolean isTypesDistinct() {
        if (settings == null) {
            return true;
        }
        List<NotificationType> types = settings.stream()
                .filter(entry -> entry != null && entry.getType() != null)
                .map(NotificationSettingEntry::getType)
                .toList();
        return types.stream().distinct().count() == types.size();
    }
}
