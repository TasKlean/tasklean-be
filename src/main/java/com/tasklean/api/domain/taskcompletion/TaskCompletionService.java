package com.tasklean.api.domain.taskcompletion;

import com.tasklean.api.common.ErrorCodes;
import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.KeysetCursor;
import com.tasklean.api.common.exception.BusinessRuleException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditChanges;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.task.RecurrenceSchedule;
import com.tasklean.api.domain.task.Task;
import com.tasklean.api.domain.task.TaskRepository;
import com.tasklean.api.domain.task.TaskStatus;
import com.tasklean.api.domain.taskcompletion.dto.CompleteTaskRequest;
import com.tasklean.api.domain.taskcompletion.dto.SkipTaskRequest;
import com.tasklean.api.domain.taskcompletion.dto.TaskCompletionResponse;
import com.tasklean.api.domain.taskcompletion.dto.TaskHistoryPage;
import com.tasklean.api.domain.taskcompletion.dto.TaskHistoryQueryRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Recording what became of one scheduled instance of a chore, and taking it back. Every write here
 * locks the task's row first: the schedule is read and then written forward, so two people
 * finishing the same chore at once must not both read the same due date.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskCompletionService {

    private final TaskCompletionRepository taskCompletionRepository;
    private final TaskRepository taskRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final RecurrenceSchedule recurrenceSchedule;
    private final KeysetCursor keysetCursor;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /**
     * Records a chore as done. An unassigned chore is claimed by the caller in the same call -
     * doing it is what takes it, and asking somebody to assign it to themselves first would only
     * ever be answered yes.
     *
     * @param taskUid      the task's public UID
     * @param request      the note and, optionally, how long it really took
     * @param callerUserId the acting user, from the JWT
     * @return the instance recorded, and the state the task was left in
     * @throws ResourceNotFoundException if no active task has that UID
     * @throws AccessDeniedException     if the caller is not a member of the task's household
     * @throws BusinessRuleException     if the chore is archived, or this instance is already recorded
     */
    @Transactional
    public TaskCompletionResponse complete(String taskUid, CompleteTaskRequest request, Long callerUserId) {
        return record(taskUid, TaskOutcome.DONE, request.getNote(), request.getMinutes(), callerUserId);
    }

    /**
     * Records a chore as skipped - the instance happened and nobody did it, which is a different
     * fact from it never having come round. The schedule moves on exactly as it does for a done one.
     *
     * @param taskUid      the task's public UID
     * @param request      the note saying why
     * @param callerUserId the acting user, from the JWT
     * @return the instance recorded, and the state the task was left in
     * @throws ResourceNotFoundException if no active task has that UID
     * @throws AccessDeniedException     if the caller is not a member of the task's household
     * @throws BusinessRuleException     if the chore is archived, or this instance is already recorded
     */
    @Transactional
    public TaskCompletionResponse skip(String taskUid, SkipTaskRequest request, Long callerUserId) {
        return record(taskUid, TaskOutcome.SKIPPED, request.getNote(), null, callerUserId);
    }

    /**
     * Returns a page of a task's history, most recently due first.
     *
     * @param taskUid the task's public UID
     * @param query   the cursor and page size
     * @return the page, with a cursor when further instances exist
     * @throws ResourceNotFoundException if no active task has that UID
     */
    public TaskHistoryPage getHistory(String taskUid, TaskHistoryQueryRequest query) {
        Task task = taskRepository.findByUidAndIsActiveTrue(taskUid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TASK_NOT_FOUND));

        int limit = query.getLimit() == null ? 20 : query.getLimit();
        KeysetCursor.Position after = keysetCursor.decode(query.getCursor());
        // One row more than the page holds, so a next cursor means a further instance really exists
        // rather than being handed out on every page including the last.
        List<TaskCompletion> read = after == null
                ? taskCompletionRepository.findHistory(task.getIdTask(), Limit.of(limit + 1))
                : taskCompletionRepository.findHistoryAfter(task.getIdTask(), after.timestamp(),
                        after.id(), Limit.of(limit + 1));
        List<TaskCompletion> items = read.size() > limit ? read.subList(0, limit) : read;

        return TaskHistoryPage.builder()
                .items(items.stream().map(TaskCompletionResponse::from).toList())
                .nextCursor(read.size() > limit
                        ? keysetCursor.encode(items.getLast().getDateCompleted(),
                                items.getLast().getIdTaskCompletion())
                        : null)
                .total(taskCompletionRepository.countByTask(task.getIdTask()))
                .build();
    }

    /**
     * Returns one instance by its id.
     *
     * @param id the instance's id
     * @return the instance
     * @throws ResourceNotFoundException if there is no such active instance
     */
    public TaskCompletionResponse getCompletion(Long id) {
        return TaskCompletionResponse.from(active(id));
    }

    /**
     * Takes back an instance: the row goes inactive and the task's schedule is put back where it
     * was. Only the most recently recorded instance may be undone - putting the anchor back to an
     * earlier one would leave the schedule behind instances that already happened.
     *
     * <p>The stored {@code dueDate} is what makes this exact rather than a guess, and the unique
     * index being partial on {@code is_active} is what lets the freed instance be recorded again.
     *
     * @param id           the instance's id
     * @param callerUserId the acting user, from the JWT
     * @return the task's state after the undo, carried on the instance that was taken back
     * @throws ResourceNotFoundException if there is no such active instance
     * @throws BusinessRuleException     if a later instance has been recorded since
     */
    @Transactional
    public TaskCompletionResponse undo(Long id, Long callerUserId) {
        TaskCompletion completion = active(id);
        // Locked before the latest-instance check, or a completion landing in between would make
        // this undo the second-newest and move the anchor backwards past it.
        Task task = taskRepository.findForUpdateByUid(completion.getTask().getUid())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TASK_NOT_FOUND));

        Long latest = taskCompletionRepository.findLatestByTask(task.getIdTask())
                .map(TaskCompletion::getIdTaskCompletion)
                .orElse(null);
        if (!id.equals(latest)) {
            throw new BusinessRuleException(
                    "Only the most recent entry in a chore's history can be undone",
                    ErrorCodes.TASK_COMPLETION_NOT_LATEST);
        }

        completion.setIsActive(false);
        taskCompletionRepository.save(completion);

        // Put the schedule back to the instance being taken back. A one-off was closed by the
        // completion, so undoing it reopens the chore rather than leaving a done task with no
        // completion behind it.
        task.setNextDueDate(completion.getDueDate());
        task.setStatus(TaskStatus.TODO);
        Task saved = taskRepository.save(task);

        log.info("Task instance undone: id={} taskUid={}", id, saved.getUid());
        auditLogService.recordEvent(AuditEntityType.TASK_COMPLETION, id, AuditAction.COMPLETION_UNDONE,
                "Entry in \"" + saved.getName() + "\" history undone", saved.getGroup());
        return TaskCompletionResponse.withTaskState(completion, saved.getStatus(), saved.getNextDueDate());
    }

    private TaskCompletionResponse record(String taskUid, TaskOutcome outcome, String note,
                                          Integer minutes, Long callerUserId) {
        Task task = taskRepository.findForUpdateByUid(taskUid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TASK_NOT_FOUND));
        if (task.getStatus() == TaskStatus.ARCHIVED) {
            throw new BusinessRuleException("An archived chore cannot be recorded against",
                    ErrorCodes.TASK_ARCHIVED);
        }

        GroupMember caller = memberOf(task, callerUserId);
        GroupMember assignee = task.getAssignedTo();
        if (assignee != null && !assignee.getIdGroupMember().equals(caller.getIdGroupMember())) {
            // Somebody else's chore. Not a 404: the caller can see it on the board, so pretending
            // it is not there would be a worse answer than saying it is not theirs.
            throw new AccessDeniedException(ErrorMessages.ACCESS_DENIED);
        }

        LocalDateTime dueDate = task.getNextDueDate();
        requireRoundIsDue(dueDate);
        // Not the double-tap guard - that is requireRoundIsDue, since the lock means a second
        // caller reads an anchor the first already moved. This catches an anchor moved back onto
        // a recorded round, and turns the unique index's 500 into a 409.
        if (dueDate != null
                && taskCompletionRepository.existsByTaskIdTaskAndDueDateAndIsActiveTrue(task.getIdTask(), dueDate)) {
            throw new BusinessRuleException("This round of the chore is already recorded",
                    ErrorCodes.TASK_INSTANCE_ALREADY_RECORDED);
        }
        if (dueDate == null && task.getStatus() == TaskStatus.COMPLETED) {
            // A one-off with no due date has no instance key to collide on, so its status is the
            // only thing standing between it and a second completion.
            throw new BusinessRuleException("This chore is already done",
                    ErrorCodes.TASK_INSTANCE_ALREADY_RECORDED);
        }

        boolean claimed = assignee == null;
        if (claimed) {
            task.setAssignedTo(caller);
        }

        TaskCompletion completion = taskCompletionRepository.save(TaskCompletion.builder()
                .task(task)
                .attributedTo(caller)
                .dueDate(dueDate)
                .outcome(outcome)
                // The estimate is snapshotted when nothing better is reported, so a later edit to
                // the estimate cannot rewrite what the share said last month. A skipped round gets
                // none of it: nobody spent that time, and the share must not read as though they had.
                .minutes(outcome == TaskOutcome.DONE
                        ? (minutes != null ? minutes : task.getEstimatedTimeMinutes())
                        : null)
                .completionNote(note)
                .dateCompleted(LocalDateTime.now(clock))
                .build());

        advanceSchedule(task, dueDate);
        Task saved = taskRepository.save(task);

        log.info("Task instance recorded: id={} taskUid={} outcome={} claimed={}",
                completion.getIdTaskCompletion(), saved.getUid(), outcome, claimed);
        auditLogService.recordEvent(AuditEntityType.TASK_COMPLETION, completion.getIdTaskCompletion(),
                outcome == TaskOutcome.DONE ? AuditAction.COMPLETE : AuditAction.TASK_SKIPPED,
                "Task \"" + saved.getName() + "\" " + (outcome == TaskOutcome.DONE ? "done" : "skipped"),
                saved.getGroup());
        if (claimed) {
            // The same entry TaskService.claimTask writes, named the same way: one action must not
            // say "assignedToUid moved" down one path and nothing at all down the other.
            auditLogService.recordEvent(AuditEntityType.TASK, saved.getIdTask(), AuditAction.TASK_CLAIMED,
                    "Task \"" + saved.getName() + "\" claimed", saved.getGroup(),
                    AuditChanges.create().track("assignedToUid", null, caller.getIdGroupMember()));
        }
        return TaskCompletionResponse.withTaskState(completion, saved.getStatus(), saved.getNextDueDate());
    }

    /**
     * Refuses a round that has not come round yet. This is also what stops a double tap recording
     * two rounds: the first one rolls the schedule into the future, so the second has nothing due.
     *
     * <p>Compared by date rather than by instant. A due date carries the time of day the chore was
     * created at, which is an artefact rather than a deadline - comparing instants would leave a
     * chore due Saturday unrecordable until Saturday evening.
     */
    private void requireRoundIsDue(LocalDateTime dueDate) {
        if (dueDate != null && dueDate.toLocalDate().isAfter(LocalDate.now(clock))) {
            throw new BusinessRuleException("This chore is not due yet",
                    ErrorCodes.TASK_ROUND_NOT_DUE);
        }
    }

    // A recurring chore comes round again; a one-off is finished. The roll counts periods from the
    // due date rather than from now, so a weekly Monday chore done on Thursday is next due Monday.
    private void advanceSchedule(Task task, LocalDateTime dueDate) {
        if (task.getRecurrenceType() == null) {
            task.setStatus(TaskStatus.COMPLETED);
            task.setDateCompleted(LocalDateTime.now(clock));
            return;
        }
        task.setNextDueDate(recurrenceSchedule.rollForward(task.getRecurrenceType(),
                task.getRecurrencePattern(), dueDate, LocalDateTime.now(clock)));
        task.setStatus(TaskStatus.TODO);
    }

    private TaskCompletion active(Long id) {
        return taskCompletionRepository.findDetailedByIdTaskCompletion(id)
                .filter(tc -> Boolean.TRUE.equals(tc.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TASK_COMPLETION_NOT_FOUND));
    }

    private GroupMember memberOf(Task task, Long callerUserId) {
        return Optional.ofNullable(callerUserId)
                .flatMap(userId -> groupMemberRepository
                        .findByUserIdUserAndGroupIdGroup(userId, task.getGroup().getIdGroup()))
                .filter(member -> Boolean.TRUE.equals(member.getIsActive()))
                .orElseThrow(() -> new AccessDeniedException(ErrorMessages.ACCESS_DENIED));
    }
}
