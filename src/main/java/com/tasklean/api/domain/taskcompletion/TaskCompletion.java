package com.tasklean.api.domain.taskcompletion;

import com.tasklean.api.domain.task.Task;
import com.tasklean.api.domain.groupmember.GroupMember;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One scheduled instance of a task and what became of it. A recurring task accumulates a row per
 * instance rather than a calendar of future ones, so this is both the completion history and the
 * record of what was skipped or missed.
 */
@Entity
@Table(name = "task_completion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_task_completion")
    private Long idTaskCompletion;

    /** The instance this row is for — null on a one-off task, which has no schedule. */
    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 50)
    private TaskOutcome outcome = TaskOutcome.DONE;

    @Column(name = "completion_photo_uid", length = 100)
    private String completionPhotoUid;

    @Column(name = "completion_note", columnDefinition = "TEXT")
    private String completionNote;

    /**
     * The task's estimated minutes as they stood when this was recorded. Snapshot rather than read
     * through, or editing a task would retroactively revalue every share it ever counted toward.
     */
    @Column(name = "minutes")
    private Integer minutes;

    @Column(name = "date_completed", nullable = false)
    private LocalDateTime dateCompleted;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "date_created", nullable = false, updatable = false)
    private LocalDateTime dateCreated;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    /**
     * Who this instance is attributed to — who did it, or who was on the hook for one nobody did.
     * Null when the task was unassigned, since a miss then belongs to no one in particular.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_member_id")
    private GroupMember attributedTo;
}
