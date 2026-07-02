package com.tasklean.api.domain.task.dto;

import com.tasklean.api.domain.task.Task;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskResponse {

    private Long id;
    private String uid;
    private String name;
    private String description;
    private String taskPhotoUrl;
    private String priority;
    private String recurrenceType;
    private Map<String, Object> recurrencePattern;
    private LocalDateTime nextDueDate;
    private Boolean requiresPhotoProof;
    private Integer estimatedTimeMinutes;
    private String status;
    private Boolean isActive;
    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;
    private LocalDateTime dateCompleted;
    private Long groupId;
    private Long createdById;
    private Long assignedToId;
    private Long categoryId;

    public static TaskResponse from(Task task) {
        return TaskResponse.builder()
                .id(task.getIdTask())
                .uid(task.getUid())
                .name(task.getName())
                .description(task.getDescription())
                .taskPhotoUrl(task.getTaskPhotoUrl())
                .priority(task.getPriority())
                .recurrenceType(task.getRecurrenceType())
                .recurrencePattern(task.getRecurrencePattern())
                .nextDueDate(task.getNextDueDate())
                .requiresPhotoProof(task.getRequiresPhotoProof())
                .estimatedTimeMinutes(task.getEstimatedTimeMinutes())
                .status(task.getStatus())
                .isActive(task.getIsActive())
                .dateCreated(task.getDateCreated())
                .dateUpdated(task.getDateUpdated())
                .dateCompleted(task.getDateCompleted())
                .groupId(task.getGroup().getIdGroup())
                .createdById(task.getCreatedBy().getIdGroupMember())
                .assignedToId(task.getAssignedTo() != null ? task.getAssignedTo().getIdGroupMember() : null)
                .categoryId(task.getCategory() != null ? task.getCategory().getIdCategory() : null)
                .build();
    }
}
