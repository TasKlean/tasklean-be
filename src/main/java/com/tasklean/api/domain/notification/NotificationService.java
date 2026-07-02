package com.tasklean.api.domain.notification;

import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public List<NotificationResponse> getNotificationsByUser(Long userId) {
        return notificationRepository.findByUserIdUserOrderByDateCreatedDesc(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    public List<NotificationResponse> getUnreadNotificationsByUser(Long userId) {
        return notificationRepository.findByUserIdUserAndIsReadFalseOrderByDateCreatedDesc(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdUserAndIsReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notification.setIsRead(true);
        notification.setReadAt(LocalDateTime.now());
        return NotificationResponse.from(notificationRepository.save(notification));
    }
}
