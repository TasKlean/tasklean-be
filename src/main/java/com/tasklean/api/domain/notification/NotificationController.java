package com.tasklean.api.domain.notification;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.notification.dto.NotificationResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for notifications — list, list unread, unread count, and mark read.
 */
@Tag(name = "Notifications", description = "In-app notifications for the signed-in user.")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Lists a user's notifications, newest first.
     *
     * @param userId the user id
     * @return {@code 200 OK} with the notifications
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotificationsByUser(@RequestParam Long userId) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getNotificationsByUser(userId)));
    }

    /**
     * Lists a user's unread notifications, newest first.
     *
     * @param userId the user id
     * @return {@code 200 OK} with the unread notifications
     */
    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getUnreadNotifications(@RequestParam Long userId) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getUnreadNotificationsByUser(userId)));
    }

    /**
     * Returns a user's unread notification count.
     *
     * @param userId the user id
     * @return {@code 200 OK} with the count
     */
    @GetMapping("/unread/count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(@RequestParam Long userId) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getUnreadCount(userId)));
    }

    /**
     * Marks a notification as read.
     *
     * @param id the notification id
     * @return {@code 200 OK} with the updated notification
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.markAsRead(id)));
    }
}
