package com.tasklean.api.domain.taskcompletion.dto;

import com.tasklean.api.domain.task.TaskStatus;
import com.tasklean.api.domain.taskcompletion.TaskCompletion;
import com.tasklean.api.domain.taskcompletion.TaskOutcome;
import com.tasklean.api.domain.groupmember.dto.MemberSummaryResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskCompletionResponse {

    private Long id;
    private LocalDateTime dueDate;
    private TaskOutcome outcome;
    private String completionPhotoUid;
    private String completionNote;
    private Integer minutes;
    private LocalDateTime dateCompleted;
    private LocalDateTime dateCreated;

    /** The task by its uid, never its row id - an instance is addressed by id, its task is not. */
    private String taskUid;
    private String taskName;
    private MemberSummaryResponse attributedTo;

    /**
     * Where the task stands now that this instance is recorded. Carried on the action's own answer
     * so a client need not re-read the task to redraw the card it just acted on.
     */
    private TaskStatus taskStatus;
    private LocalDateTime taskNextDueDate;

    public static TaskCompletionResponse from(TaskCompletion completion) {
        return base(completion).build();
    }

    /**
     * The instance plus the state its task was left in - the answer to completing or skipping.
     *
     * @param completion the instance just recorded
     * @param status     the task's status after it
     * @param nextDue    the task's next due date after it, or {@code null} if it has none
     * @return the response
     */
    public static TaskCompletionResponse withTaskState(TaskCompletion completion, TaskStatus status,
                                                       LocalDateTime nextDue) {
        return base(completion).taskStatus(status).taskNextDueDate(nextDue).build();
    }

    private static TaskCompletionResponseBuilder base(TaskCompletion completion) {
        return TaskCompletionResponse.builder()
                .id(completion.getIdTaskCompletion())
                .dueDate(completion.getDueDate())
                .outcome(completion.getOutcome())
                .completionPhotoUid(completion.getCompletionPhotoUid())
                .completionNote(completion.getCompletionNote())
                .minutes(completion.getMinutes())
                .dateCompleted(completion.getDateCompleted())
                .dateCreated(completion.getDateCreated())
                .taskUid(completion.getTask().getUid())
                .taskName(completion.getTask().getName())
                .attributedTo(MemberSummaryResponse.from(completion.getAttributedTo()));
    }

}
