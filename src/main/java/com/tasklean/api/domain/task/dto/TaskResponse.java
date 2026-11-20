package com.tasklean.api.domain.task.dto;

import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.tag.Tag;
import com.tasklean.api.domain.tag.dto.TagResponse;
import com.tasklean.api.domain.task.RecurrenceType;
import com.tasklean.api.domain.task.Task;
import com.tasklean.api.domain.task.TaskPriority;
import com.tasklean.api.domain.task.TaskStatus;
import com.tasklean.api.domain.user.dto.UserSummaryResponse;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A task as returned by the API. Carries the people behind its author and assignee rather than
 * ids, so a client can render it without a second call. Its tags are passed in rather than read
 * off the task: they live in their own join table, which a task entity deliberately knows
 * nothing about.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskResponse {

    private String uid;
    private String name;
    private String description;
    private TaskPriority priority;
    private RecurrenceType recurrenceType;
    private LocalDateTime nextDueDate;
    private Boolean requiresPhotoProof;

    private String photoUid;
    private Integer estimatedTimeMinutes;
    private TaskStatus status;
    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;
    private LocalDateTime dateCompleted;
    private String groupUid;
    private UserSummaryResponse createdBy;
    private UserSummaryResponse assignedTo;
    private Long categoryId;
    private List<TagResponse> tags;

    /** Derived, never stored: a stored flag is wrong from the due moment until something writes. */
    private Boolean overdue;

    public static TaskResponse from(Task task, List<Tag> tags, LocalDateTime now) {
        return TaskResponse.builder()
                .uid(task.getUid())
                .name(task.getName())
                .description(task.getDescription())
                .priority(task.getPriority())
                .recurrenceType(task.getRecurrenceType())
                .nextDueDate(task.getNextDueDate())
                .requiresPhotoProof(task.getRequiresPhotoProof())
                .photoUid(task.getPhotoUid())
                .estimatedTimeMinutes(task.getEstimatedTimeMinutes())
                .status(task.getStatus())
                .dateCreated(task.getDateCreated())
                .dateUpdated(task.getDateUpdated())
                .dateCompleted(task.getDateCompleted())
                .groupUid(task.getGroup().getUid())
                // created_by and assigned_to reference a membership; a client wants the person.
                .createdBy(person(task.getCreatedBy()))
                .assignedTo(person(task.getAssignedTo()))
                .categoryId(task.getCategory() != null ? task.getCategory().getIdCategory() : null)
                .tags(tags.stream().map(TagResponse::from).toList())
                .overdue(overdue(task, now))
                .build();
    }

    private static UserSummaryResponse person(GroupMember membership) {
        return membership == null ? null : UserSummaryResponse.from(membership.getUser());
    }

    // One definition of late, shared with the board's filter: only a chore still waiting to be
    // done can be late, and an undated one has nothing to be late for.
    private static boolean overdue(Task task, LocalDateTime now) {
        return task.getNextDueDate() != null
                && task.getNextDueDate().isBefore(now)
                && TaskStatus.OPEN.contains(task.getStatus());
    }
}
