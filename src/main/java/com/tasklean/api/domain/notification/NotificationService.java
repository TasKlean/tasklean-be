package com.tasklean.api.domain.notification;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Manages user notifications — read queries, unread counts, and marking notifications read.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    /**
     * Returns all notifications for a user, newest first.
     *
     * @param userId the user id
     * @return the user's notifications
     */
    public List<NotificationResponse> getNotificationsByUser(Long userId) {
        return notificationRepository.findByUserIdUserOrderByDateCreatedDesc(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    /**
     * Returns a user's unread notifications, newest first.
     *
     * @param userId the user id
     * @return the user's unread notifications
     */
    public List<NotificationResponse> getUnreadNotificationsByUser(Long userId) {
        return notificationRepository.findByUserIdUserAndIsReadFalseOrderByDateCreatedDesc(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    /**
     * Returns the count of a user's unread notifications.
     *
     * @param userId the user id
     * @return the unread count
     */
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdUserAndIsReadFalse(userId);
    }

    /**
     * Marks a notification as read and stamps the read time.
     *
     * @param id the notification id
     * @return the updated notification
     * @throws ResourceNotFoundException if no notification has that id
     */
    @Transactional
    public NotificationResponse markAsRead(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.NOTIFICATION_NOT_FOUND));
        notification.setIsRead(true);
        notification.setReadAt(LocalDateTime.now(clock));
        return NotificationResponse.from(notificationRepository.save(notification));
    }
}
