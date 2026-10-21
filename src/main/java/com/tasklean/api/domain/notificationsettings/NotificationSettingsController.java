package com.tasklean.api.domain.notificationsettings;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.notification.NotificationType;
import com.tasklean.api.domain.notificationsettings.dto.NotificationSettingRequest;
import com.tasklean.api.domain.notificationsettings.dto.NotificationSettingResponse;
import com.tasklean.api.domain.notificationsettings.dto.NotificationSettingsBatchRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The caller's own notification settings — which channels deliver which notification type. The
 * user is always resolved from the JWT principal; the only path variable is the notification
 * type, so a user can never read or change anyone else's settings.
 */
@Tag(name = "Notification settings", description = "The caller's own notification settings: which channels deliver which notification type.")
@RestController
@RequestMapping("/api/notification-settings")
@RequiredArgsConstructor
public class NotificationSettingsController {

    private final NotificationSettingsService notificationSettingsService;

    /**
     * Lists the caller's settings for every notification type, using the defaults for any type
     * they have never saved.
     *
     * @param principal the authenticated caller
     * @return {@code 200 OK} with one entry per notification type
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationSettingResponse>>> getMyNotificationSettings(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationSettingsService.getMySettings(principal.userId())));
    }

    /**
     * Applies a batch of entries at once — each one replaces that type's channels, and types the
     * batch leaves out keep their current values. Declared above {@code /{type}} since it is the
     * literal sibling of that template.
     *
     * @param principal the authenticated caller
     * @param request   the entries to apply
     * @return {@code 200 OK} with the caller's whole matrix as persisted after the batch
     */
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/batch")
    public ResponseEntity<ApiResponse<List<NotificationSettingResponse>>> updateMyNotificationSettings(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody NotificationSettingsBatchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationSettingsService.updateMySettings(principal.userId(), request)));
    }

    /**
     * Replaces the caller's channels for one notification type, creating the entry on first write.
     *
     * @param principal the authenticated caller
     * @param type      the notification type to configure
     * @param request   the channel preferences
     * @return {@code 200 OK} with the saved entry
     */
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{type}")
    public ResponseEntity<ApiResponse<NotificationSettingResponse>> updateMyNotificationSetting(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable NotificationType type,
            @Valid @RequestBody NotificationSettingRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationSettingsService.updateMySetting(principal.userId(), type, request)));
    }
}
