package com.tasklean.api.domain.notification.dto;

import com.tasklean.api.domain.notification.Notification;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private Long id;
    private String type;
    private String title;
    private String message;
    private Boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime dateCreated;
    private Long userId;
    private Long createdById;
    private Long taskId;
    private Long groupId;

    public static NotificationResponse from(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getIdNotification())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .isRead(notification.getIsRead())
                .readAt(notification.getReadAt())
                .dateCreated(notification.getDateCreated())
                .userId(notification.getUser().getIdUser())
                .createdById(notification.getCreatedBy() != null ? notification.getCreatedBy().getIdUser() : null)
                .taskId(notification.getTask() != null ? notification.getTask().getIdTask() : null)
                .groupId(notification.getGroup() != null ? notification.getGroup().getIdGroup() : null)
                .build();
    }
}
