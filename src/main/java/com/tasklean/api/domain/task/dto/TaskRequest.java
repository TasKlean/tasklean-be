package com.tasklean.api.domain.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskRequest {

    @NotBlank
    private String name;

    private String description;

    private String taskPhotoUrl;

    @NotBlank
    private String priority;

    private String recurrenceType;

    private Map<String, Object> recurrencePattern;

    private LocalDateTime nextDueDate;

    private Boolean requiresPhotoProof;

    private Integer estimatedTimeMinutes;

    @NotBlank
    private String status;

    @NotNull
    private Long groupId;

    @NotNull
    private Long createdById;

    private Long assignedToId;

    private Long categoryId;
}
