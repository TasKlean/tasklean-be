package com.tasklean.api.domain.task.dto;

import com.tasklean.api.common.validation.FieldLimits;
import com.tasklean.api.domain.task.RecurrenceType;
import com.tasklean.api.domain.task.TaskPriority;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The editable state of a task. Carries neither its group nor its author, because an edit can
 * change neither, and no status — completing and archiving are their own operations.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskUpdateRequest {

    @NotBlank
    @Size(min = 1, max = FieldLimits.TASK_NAME)
    private String name;

    @Size(max = FieldLimits.DESCRIPTION)
    private String description;

    @NotNull
    private TaskPriority priority;

    /** How often the task recurs. Omitted, it happens once. */
    private RecurrenceType recurrenceType;

    /**
     * When the task is next due. Required for a recurring task, since it is the schedule's only
     * anchor — a weekly chore recurs on whichever day this falls on. Carries an offset so the
     * stored UTC instant is the one the caller meant.
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime nextDueDate;

    private Boolean requiresPhotoProof;

    @Min(1)
    @Max(FieldLimits.TASK_MINUTES)
    private Integer estimatedTimeMinutes;

    /** The membership uid of whoever it is for. Omitted, the task sits in the household's pool. */
    @Size(max = FieldLimits.UID)
    private String assignedToUid;

    private Long categoryId;

    /**
     * The labels on the task, as a member types them. A name the household does not have yet
     * becomes a new tag; one it already has is matched regardless of case, so the same label is
     * never added twice. Absent, the task carries no tags — like every other field here, this is
     * the task's whole state, not a change to part of it.
     */
    @Size(max = FieldLimits.TASK_TAGS)
    private List<@NotBlank @Size(min = 1, max = FieldLimits.LABEL_NAME) String> tagNames;

    /** An uploaded picture of what "done" looks like, signed for this task's household. */
    @Size(max = FieldLimits.UID)
    private String photoUid;
}
