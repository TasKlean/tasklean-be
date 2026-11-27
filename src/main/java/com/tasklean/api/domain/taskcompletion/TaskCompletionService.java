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
import com.tasklean.api.domain.upload.UploadService;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.task.RecurrenceSchedule;
import com.tasklean.api.domain.task.TaskAssignmentReset;
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
    private final TaskAssignmentReset assignmentReset;
    private final KeysetCursor keysetCursor;
    private final AuditLogService auditLogService;
    private final UploadService uploadService;
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
     * @throws BusinessRuleException     if the chore is archived, this instance is already
     *                                   recorded, or a round was already done ahead of schedule
     */
    @Transactional
    public TaskCompletionResponse complete(String taskUid, CompleteTaskRequest request, Long callerUserId) {
        return record(taskUid, TaskOutcome.DONE, request.getNote(), request.getMinutes(),
                request.getPhotoUid(), callerUserId);
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
     * @throws BusinessRuleException     if the chore is archived, this instance is already
     *                                   recorded, or the round has not come round yet
     */
    @Transactional
    public TaskCompletionResponse skip(String taskUid, SkipTaskRequest request, Long callerUserId) {
        return record(taskUid, TaskOutcome.SKIPPED, request.getNote(), null, null, callerUserId);
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
     * @param id the instance's id
     * @return the task's state after the undo, carried on the instance that was taken back
     * @throws ResourceNotFoundException if there is no such active instance
     * @throws BusinessRuleException     if a later instance has been recorded since
     */
    @Transactional
    public TaskCompletionResponse undo(Long id) {
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
        // An undone round is unreachable, so its proof should not stay a paid-for object.
        uploadService.releaseCompletionPhoto(completion.getCompletionPhotoUid());
        taskCompletionRepository.save(completion);

        // Put the schedule back to the instance being taken back. A one-off was closed by the
        // completion, so undoing it reopens the chore rather than leaving a done task with no
        // completion behind it.
        task.setNextDueDate(completion.getDueDate());
        task.setStatus(TaskStatus.TODO);
        // Undo restores the schedule, not the assignment. The record cannot tell a round that was
        // somebody's from one they picked up, so a repeating chore goes back to its owner.
        if (task.getRecurrenceType() != null) {
            assignmentReset.toDefault(task);
        }
        Task saved = taskRepository.save(task);

        log.info("Task instance undone: id={} taskUid={}", id, saved.getUid());
        auditLogService.recordEvent(AuditEntityType.TASK_COMPLETION, id, AuditAction.COMPLETION_UNDONE,
                "Entry in \"" + saved.getName() + "\" history undone", saved.getGroup());
        return TaskCompletionResponse.withTaskState(completion, saved.getStatus(), saved.getNextDueDate());
    }

    private TaskCompletionResponse record(String taskUid, TaskOutcome outcome, String note,
                                          Integer minutes, String photoUid, Long callerUserId) {
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

        // Only a chore being done needs proof of it. A skip asserts the work did not happen, so
        // demanding a picture of it would be incoherent.
        if (outcome == TaskOutcome.DONE
                && Boolean.TRUE.equals(task.getRequiresPhotoProof()) && photoUid == null) {
            throw new BusinessRuleException("This chore needs a photo to be marked done",
                    ErrorCodes.TASK_PHOTO_PROOF_REQUIRED);
        }

        LocalDateTime dueDate = task.getNextDueDate();
        requireRecordable(task, outcome, dueDate);
        // A round written off as missed is still the caller's to do, so its row is reused rather
        // than refused - uq_tc_task_due allows one live row per round, and the work really did
        // happen. Anything else already there is the double tap, and the 409 the index would be.
        TaskCompletion existing = dueDate == null ? null
                : taskCompletionRepository
                        .findByTaskIdTaskAndDueDateAndIsActiveTrue(task.getIdTask(), dueDate)
                        .orElse(null);
        if (existing != null && existing.getOutcome() != TaskOutcome.MISSED) {
            throw new BusinessRuleException("This round of the chore is already recorded",
                    ErrorCodes.TASK_INSTANCE_ALREADY_RECORDED);
        }
        if (dueDate == null && task.getStatus() == TaskStatus.COMPLETED) {
            // A one-off with no due date has no instance key to collide on, so its status is the
            // only thing standing between it and a second completion.
            throw new BusinessRuleException("This chore is already done",
                    ErrorCodes.TASK_INSTANCE_ALREADY_RECORDED);
        }

        // A one-off is finished by this, so whoever did it keeps it: there is no next round for
        // an owner to take back. A repeating chore is handed back below instead.
        boolean claimed = assignee == null && task.getRecurrenceType() == null;

        TaskCompletion completion = existing != null ? existing
                : TaskCompletion.builder().task(task).dueDate(dueDate).build();
        completion.setAttributedTo(caller);
        completion.setOutcome(outcome);
        // The estimate is snapshotted when nothing better is reported, so a later edit to the
        // estimate cannot rewrite what the share said last month. A skipped round gets none of it:
        // nobody spent that time, and the share must not read as though they had.
        completion.setMinutes(outcome == TaskOutcome.DONE
                ? (minutes != null ? minutes : task.getEstimatedTimeMinutes())
                : null);
        completion.setCompletionPhotoUid(
                uploadService.attachCompletionPhoto(photoUid, task, callerUserId));
        completion.setCompletionNote(note);
        completion.setDateCompleted(LocalDateTime.now(clock));
        taskCompletionRepository.save(completion);

        advanceSchedule(task, dueDate);
        if (claimed) {
            task.setAssignedTo(caller);
        }
        // Recording a round credits a person, it does not hand them the chore: a repeating one
        // goes back to its owner, or to nobody, so the next round is open to whoever it was.
        boolean handedBack = task.getRecurrenceType() != null && assignmentReset.toDefault(task);
        Task saved = taskRepository.save(task);

        log.info("Task instance recorded: id={} taskUid={} outcome={} claimed={} wasMissed={}",
                completion.getIdTaskCompletion(), saved.getUid(), outcome, claimed, existing != null);
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
        if (handedBack) {
            // Only when it really changes hands: a chore that comes back to the same owner every
            // round would otherwise file an entry saying so, for ever, on an append-only trail.
            auditLogService.recordEvent(AuditEntityType.TASK, saved.getIdTask(), AuditAction.TASK_ASSIGNED,
                    "Task \"" + saved.getName() + "\" handed back", saved.getGroup(),
                    AuditChanges.create().track("assignedToUid", caller.getIdGroupMember(),
                            idOf(saved.getDefaultAssignee())));
        }
        return TaskCompletionResponse.withTaskState(completion, saved.getStatus(), saved.getNextDueDate());
    }

    /**
     * Refuses a round that cannot be recorded yet. A chore may be done <em>before</em> its due
     * date, because the work really did happen - but only one round ahead, and a skip needs the
     * round to have come round at all, since it asserts that one went by and nobody did it.
     *
     * <p>The cap is also what closes a double tap. The first request rolls the anchor into the
     * future, and the round it recorded is then the newest entry and not yet passed, so a second
     * request has nothing left it may record.
     *
     * <p>Compared by date rather than by instant. A due date carries the time of day the chore was
     * created at, which is an artefact rather than a deadline - comparing instants would leave a
     * chore due Saturday unrecordable until Saturday evening.
     */
    private void requireRecordable(Task task, TaskOutcome outcome, LocalDateTime dueDate) {
        LocalDate today = LocalDate.now(clock);
        if (dueDate == null || !dueDate.toLocalDate().isAfter(today)) {
            return;
        }
        if (outcome != TaskOutcome.DONE) {
            // Doing a chore early is work that happened; skipping one early is a statement about a
            // round that has not come round, which would need an endpoint saying so.
            throw new BusinessRuleException("This chore is not due yet",
                    ErrorCodes.TASK_ROUND_NOT_DUE);
        }
        // An entry for a round still ahead means this chore is already running early. Read after
        // the lock, so the round a concurrent caller recorded is one this one can see.
        boolean alreadyAhead = taskCompletionRepository.findLatestByTask(task.getIdTask())
                .map(TaskCompletion::getDueDate)
                .map(round -> !round.toLocalDate().isBefore(today))
                .orElse(false);
        if (alreadyAhead) {
            throw new BusinessRuleException("This chore has already been done ahead of schedule",
                    ErrorCodes.TASK_ROUND_ALREADY_AHEAD);
        }
    }

    private Long idOf(GroupMember member) {
        return member != null ? member.getIdGroupMember() : null;
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
