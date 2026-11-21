package com.tasklean.api.domain.taskcompletion;

import com.tasklean.api.common.KeysetCursor;
import com.tasklean.api.common.exception.BusinessRuleException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditChanges;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.task.RecurrenceSchedule;
import com.tasklean.api.domain.task.RecurrenceType;
import com.tasklean.api.domain.task.Task;
import com.tasklean.api.domain.task.TaskRepository;
import com.tasklean.api.domain.task.TaskStatus;
import com.tasklean.api.domain.taskcompletion.dto.CompleteTaskRequest;
import com.tasklean.api.domain.taskcompletion.dto.SkipTaskRequest;
import com.tasklean.api.domain.taskcompletion.dto.TaskCompletionResponse;
import com.tasklean.api.domain.taskcompletion.dto.TaskHistoryPage;
import com.tasklean.api.domain.taskcompletion.dto.TaskHistoryQueryRequest;
import com.tasklean.api.domain.upload.UploadService;
import com.tasklean.api.domain.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Recording what became of one round of a chore. The rules that matter here are who may record it,
 * what stops the same round being recorded twice, and what the schedule does afterwards.
 */
@ExtendWith(MockitoExtension.class)
class TaskCompletionServiceTest {

    @Mock
    private TaskCompletionRepository taskCompletionRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private RecurrenceSchedule recurrenceSchedule;
    @Mock
    private KeysetCursor keysetCursor;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private UploadService uploadService;
    @Spy
    private Clock clock = Clock.systemUTC();
    @InjectMocks
    private TaskCompletionService taskCompletionService;

    private static final String TASK_UID = "tsk-1";
    private static final Long TASK_ID = 2L;
    private static final Long GROUP_ID = 10L;
    private static final Long CALLER_USER_ID = 7L;
    private static final Long CALLER_MEMBER_ID = 5L;
    private static final LocalDateTime DUE = LocalDateTime.of(2026, 11, 16, 9, 0);

    private GroupMember member(Long id) {
        return GroupMember.builder().idGroupMember(id).isActive(true)
                .user(User.builder().idUser(CALLER_USER_ID).name("Bob").build())
                .group(Group.builder().idGroup(GROUP_ID).build())
                .build();
    }

    private Task task() {
        return Task.builder().idTask(TASK_ID).uid(TASK_UID).name("Clean").status(TaskStatus.TODO)
                .nextDueDate(DUE).estimatedTimeMinutes(15)
                .group(Group.builder().idGroup(GROUP_ID).uid("grp-1").build())
                .build();
    }

    private void locked(Task task) {
        when(taskRepository.findForUpdateByUid(TASK_UID)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));
    }

    // The paths that refuse never reach a save, so stubbing one would be an unused stub rather
    // than part of the case.
    private void lockedWithoutSaving(Task task) {
        when(taskRepository.findForUpdateByUid(TASK_UID)).thenReturn(Optional.of(task));
    }

    private void callerIsMember() {
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(CALLER_USER_ID, GROUP_ID))
                .thenReturn(Optional.of(member(CALLER_MEMBER_ID)));
    }

    private void completionSaves() {
        when(taskCompletionRepository.save(any(TaskCompletion.class))).thenAnswer(i -> {
            TaskCompletion tc = i.getArgument(0);
            tc.setIdTaskCompletion(99L);
            return tc;
        });
    }

    private TaskCompletion saved() {
        ArgumentCaptor<TaskCompletion> captor = ArgumentCaptor.forClass(TaskCompletion.class);
        verify(taskCompletionRepository).save(captor.capture());
        return captor.getValue();
    }

    private Task savedTask() {
        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        return captor.getValue();
    }

    private CompleteTaskRequest completeRequest(Integer minutes) {
        CompleteTaskRequest r = new CompleteTaskRequest();
        r.setNote("done");
        r.setMinutes(minutes);
        return r;
    }

    private TaskCompletionResponse complete(Task task) {
        locked(task);
        callerIsMember();
        completionSaves();
        return taskCompletionService.complete(TASK_UID, completeRequest(null), CALLER_USER_ID);
    }

    // ---------------------------------------------------------------- who may record

    @Test
    void complete_choreAssignedToTheCaller_isRecorded() {
        Task task = task();
        task.setAssignedTo(member(CALLER_MEMBER_ID));

        TaskCompletionResponse response = complete(task);

        assertThat(response.getOutcome()).isEqualTo(TaskOutcome.DONE);
        assertThat(saved().getAttributedTo().getIdGroupMember()).isEqualTo(CALLER_MEMBER_ID);
    }

    @Test
    void complete_unassignedChore_isClaimedInTheSameCall() {
        Task task = task();

        complete(task);

        // Doing it is what takes it: asking somebody to assign it to themselves first would only
        // ever be answered yes.
        assertThat(savedTask().getAssignedTo().getIdGroupMember()).isEqualTo(CALLER_MEMBER_ID);

        // The same entry TaskService.claimTask writes, named the same way - one action reading as
        // two things is what this guards.
        ArgumentCaptor<AuditChanges> changes = ArgumentCaptor.forClass(AuditChanges.class);
        verify(auditLogService).recordEvent(eq(AuditEntityType.TASK), eq(TASK_ID),
                eq(AuditAction.TASK_CLAIMED), any(), any(), changes.capture());
        assertThat(changes.getValue().fields()).containsExactly("assignedToUid");
    }

    @Test
    void complete_choreAssignedToSomebodyElse_isRefused() {
        Task task = task();
        task.setAssignedTo(member(999L));
        lockedWithoutSaving(task);
        callerIsMember();

        // Not a 404: the caller can see it on the board, so pretending it is not there would be a
        // worse answer than saying it is not theirs.
        assertThatThrownBy(() -> taskCompletionService.complete(TASK_UID, completeRequest(null), CALLER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(taskCompletionRepository, never()).save(any());
    }

    @Test
    void complete_callerFromAnotherHousehold_isRefused() {
        when(taskRepository.findForUpdateByUid(TASK_UID)).thenReturn(Optional.of(task()));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(CALLER_USER_ID, GROUP_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskCompletionService.complete(TASK_UID, completeRequest(null), CALLER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void complete_memberWhoHasLeft_isRefused() {
        GroupMember former = member(CALLER_MEMBER_ID);
        former.setIsActive(false);
        when(taskRepository.findForUpdateByUid(TASK_UID)).thenReturn(Optional.of(task()));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(CALLER_USER_ID, GROUP_ID))
                .thenReturn(Optional.of(former));

        assertThatThrownBy(() -> taskCompletionService.complete(TASK_UID, completeRequest(null), CALLER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void complete_deletedTask_isNotFound() {
        when(taskRepository.findForUpdateByUid(TASK_UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskCompletionService.complete(TASK_UID, completeRequest(null), CALLER_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void complete_always_readsTheTaskWithItsRowLocked() {
        complete(task());

        // The schedule is read and then written forward, so two people finishing the same chore at
        // once must not both read the same due date.
        verify(taskRepository).findForUpdateByUid(TASK_UID);
        verify(taskRepository, never()).findByUidAndIsActiveTrue(any());
    }

    // ---------------------------------------------------------------- what stops a second record

    @Test
    void complete_archivedChore_isRefused() {
        Task task = task();
        task.setStatus(TaskStatus.ARCHIVED);
        when(taskRepository.findForUpdateByUid(TASK_UID)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskCompletionService.complete(TASK_UID, completeRequest(null), CALLER_USER_ID))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void complete_roundAlreadyRecorded_isAConflictRatherThanAConstraintViolation() {
        Task task = task();
        lockedWithoutSaving(task);
        callerIsMember();
        roundHolds(task, TaskOutcome.DONE);

        assertThatThrownBy(() -> taskCompletionService.complete(TASK_UID, completeRequest(null), CALLER_USER_ID))
                .isInstanceOf(BusinessRuleException.class);
        verify(taskCompletionRepository, never()).save(any());
    }

    @Test
    void complete_roundWrittenOffAsMissed_reusesThatRowRatherThanRefusingIt() {
        Task task = task();
        locked(task);
        callerIsMember();
        completionSaves();
        TaskCompletion miss = roundHolds(task, TaskOutcome.MISSED);

        taskCompletionService.complete(TASK_UID, completeRequest(null), CALLER_USER_ID);

        // uq_tc_task_due allows one live row per round, so a late completion overwrites the miss
        // rather than inserting beside it - the work happened, and it happened on that round.
        ArgumentCaptor<TaskCompletion> saved = ArgumentCaptor.forClass(TaskCompletion.class);
        verify(taskCompletionRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(miss);
        assertThat(miss.getOutcome()).isEqualTo(TaskOutcome.DONE);
        assertThat(miss.getDueDate()).isEqualTo(DUE);
        assertThat(miss.getMinutes()).isEqualTo(15);
        assertThat(miss.getAttributedTo().getIdGroupMember()).isEqualTo(CALLER_MEMBER_ID);
    }

    @Test
    void skip_roundWrittenOffAsMissed_reusesThatRowToo() {
        Task task = task();
        locked(task);
        callerIsMember();
        completionSaves();
        TaskCompletion miss = roundHolds(task, TaskOutcome.MISSED);

        taskCompletionService.skip(TASK_UID, new SkipTaskRequest(), CALLER_USER_ID);

        // "I am not doing yesterday's" is as valid an answer as doing it, and takes the same slot.
        assertThat(miss.getOutcome()).isEqualTo(TaskOutcome.SKIPPED);
        assertThat(miss.getMinutes()).isNull();
    }

    // The one live row standing against this round, as the sweep or an earlier caller left it.
    private TaskCompletion roundHolds(Task task, TaskOutcome outcome) {
        TaskCompletion existing = TaskCompletion.builder().task(task)
                .idTaskCompletion(7L).dueDate(DUE).outcome(outcome).isActive(true).build();
        when(taskCompletionRepository.findByTaskIdTaskAndDueDateAndIsActiveTrue(TASK_ID, DUE))
                .thenReturn(Optional.of(existing));
        return existing;
    }

    @Test
    void complete_undatedOneOffAlreadyDone_isRefusedOnItsStatus() {
        Task task = task();
        task.setNextDueDate(null);
        task.setStatus(TaskStatus.COMPLETED);
        lockedWithoutSaving(task);
        callerIsMember();

        // A one-off with no due date has no instance key to collide on, so its status is the only
        // thing standing between it and a second completion.
        assertThatThrownBy(() -> taskCompletionService.complete(TASK_UID, completeRequest(null), CALLER_USER_ID))
                .isInstanceOf(BusinessRuleException.class);
        verify(taskCompletionRepository, never()).existsByTaskIdTaskAndDueDateAndIsActiveTrue(any(), any());
    }

    // ---------------------------------------------------------------- what the schedule does

    @Test
    void complete_recurringChore_rollsForwardFromTheDueDateAndStaysOpen() {
        Task task = task();
        task.setRecurrenceType(RecurrenceType.WEEKLY);
        LocalDateTime nextWeek = DUE.plusWeeks(1);
        when(recurrenceSchedule.rollForward(eq(RecurrenceType.WEEKLY), any(), eq(DUE), any()))
                .thenReturn(nextWeek);

        TaskCompletionResponse response = complete(task);

        // Counted from the due date, not from now, so a weekly Monday chore done on Thursday is
        // next due the following Monday.
        assertThat(savedTask().getNextDueDate()).isEqualTo(nextWeek);
        assertThat(savedTask().getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(response.getTaskNextDueDate()).isEqualTo(nextWeek);
    }

    @Test
    void complete_oneOffChore_isFinishedRatherThanRescheduled() {
        TaskCompletionResponse response = complete(task());

        assertThat(savedTask().getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(savedTask().getDateCompleted()).isNotNull();
        assertThat(response.getTaskStatus()).isEqualTo(TaskStatus.COMPLETED);
        verify(recurrenceSchedule, never()).rollForward(any(), any(), any(), any());
    }

    @Test
    void complete_always_recordsTheRoundThatWasDueRatherThanTheMomentItWasDone() {
        complete(task());

        // The due date is what identifies the round; when it was finished is a separate fact.
        assertThat(saved().getDueDate()).isEqualTo(DUE);
        assertThat(saved().getDateCompleted()).isNotNull();
    }

    // ---------------------------------------------------------------- minutes

    @Test
    void complete_noMinutesReported_snapshotsTheEstimate() {
        complete(task());

        // Snapshotted so a later edit to the estimate cannot rewrite what the share said last month.
        assertThat(saved().getMinutes()).isEqualTo(15);
    }

    @Test
    void complete_minutesReported_keepsWhatWasReported() {
        Task task = task();
        locked(task);
        callerIsMember();
        completionSaves();

        taskCompletionService.complete(TASK_UID, completeRequest(40), CALLER_USER_ID);

        assertThat(saved().getMinutes()).isEqualTo(40);
    }

    @Test
    void complete_noMinutesAndNoEstimate_leavesThemUnknown() {
        Task task = task();
        task.setEstimatedTimeMinutes(null);

        complete(task);

        assertThat(saved().getMinutes()).isNull();
    }

    // ---------------------------------------------------------------- skipping

    @Test
    void skip_always_recordsTheRoundAsSkippedAndStillMovesOn() {
        Task task = task();
        task.setAssignedTo(member(CALLER_MEMBER_ID));
        task.setRecurrenceType(RecurrenceType.DAILY);
        locked(task);
        callerIsMember();
        completionSaves();
        when(recurrenceSchedule.rollForward(any(), any(), eq(DUE), any())).thenReturn(DUE.plusDays(1));

        SkipTaskRequest request = new SkipTaskRequest();
        request.setNote("away this week");
        taskCompletionService.skip(TASK_UID, request, CALLER_USER_ID);

        // A skipped round happened and nobody did it, which is a different fact from it never
        // having come round - so the schedule moves on exactly as it does for a done one.
        assertThat(saved().getOutcome()).isEqualTo(TaskOutcome.SKIPPED);
        assertThat(savedTask().getNextDueDate()).isEqualTo(DUE.plusDays(1));
        verify(auditLogService).recordEvent(eq(AuditEntityType.TASK_COMPLETION), eq(99L),
                eq(AuditAction.TASK_SKIPPED), any(), any());
    }

    @Test
    void skip_always_recordsNoMinutesEvenWhenTheChoreIsEstimated() {
        Task task = task();
        task.setAssignedTo(member(CALLER_MEMBER_ID));

        locked(task);
        callerIsMember();
        completionSaves();
        taskCompletionService.skip(TASK_UID, new SkipTaskRequest(), CALLER_USER_ID);

        // Nobody spent that time, and the share must not read as though they had - which is what
        // snapshotting the estimate on every outcome alike would have done.
        assertThat(saved().getMinutes()).isNull();
    }

    // ---------------------------------------------------------------- history

    @Test
    void getHistory_moreInstancesThanThePageHolds_handsOutACursor() {
        when(taskRepository.findByUidAndIsActiveTrue(TASK_UID)).thenReturn(Optional.of(task()));
        when(taskCompletionRepository.findHistory(eq(TASK_ID), any(Limit.class)))
                .thenReturn(List.of(entry(1L, DUE), entry(2L, DUE.minusDays(1))));
        when(keysetCursor.encode(any(), anyLong())).thenReturn("next");

        TaskHistoryPage page = taskCompletionService.getHistory(TASK_UID, query(null, 1));

        // One row more than the page holds is read, so a cursor means a further instance really
        // exists rather than being handed out on every page including the last.
        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getNextCursor()).isEqualTo("next");
    }

    @Test
    void getHistory_lastPage_handsOutNoCursor() {
        when(taskRepository.findByUidAndIsActiveTrue(TASK_UID)).thenReturn(Optional.of(task()));
        when(taskCompletionRepository.findHistory(eq(TASK_ID), any(Limit.class)))
                .thenReturn(List.of(entry(1L, DUE)));

        TaskHistoryPage page = taskCompletionService.getHistory(TASK_UID, query(null, 20));

        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getNextCursor()).isNull();
    }

    @Test
    void getHistory_aCursor_resumesAfterThatPosition() {
        when(taskRepository.findByUidAndIsActiveTrue(TASK_UID)).thenReturn(Optional.of(task()));
        when(keysetCursor.decode("page-2")).thenReturn(new KeysetCursor.Position(DUE, 9L));
        when(taskCompletionRepository.findHistoryAfter(eq(TASK_ID), eq(DUE), eq(9L), any(Limit.class)))
                .thenReturn(List.of());

        taskCompletionService.getHistory(TASK_UID, query("page-2", 20));

        // A separate finder, not a null guard in the query: Postgres cannot infer a bare
        // parameter's type inside an "is null" test and refuses the statement.
        verify(taskCompletionRepository).findHistoryAfter(eq(TASK_ID), eq(DUE), eq(9L), any(Limit.class));
        verify(taskCompletionRepository, never()).findHistory(any(), any(Limit.class));
    }

    @Test
    void getHistory_always_pagesOnWhenTheRoundWasRecordedRatherThanWhenItWasDue() {
        when(taskRepository.findByUidAndIsActiveTrue(TASK_UID)).thenReturn(Optional.of(task()));
        TaskCompletion first = entry(1L, DUE);
        when(taskCompletionRepository.findHistory(eq(TASK_ID), any(Limit.class)))
                .thenReturn(List.of(first, entry(2L, null)));
        when(keysetCursor.encode(any(), anyLong())).thenReturn("next");

        taskCompletionService.getHistory(TASK_UID, query(null, 1));

        // The due date is nullable, and comparing a null one yields unknown - so a cursor built on
        // it would skip every instance of an undated chore, silently, with the page before it
        // reporting no next page at all.
        verify(keysetCursor).encode(first.getDateCompleted(), 1L);
    }

    @Test
    void getHistory_deletedTask_isNotFound() {
        when(taskRepository.findByUidAndIsActiveTrue(TASK_UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskCompletionService.getHistory(TASK_UID, query(null, 20)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- undo

    @Test
    void undo_theNewestEntry_putsTheScheduleBackWhereItWas() {
        TaskCompletion entry = entry(99L, DUE);
        Task task = task();
        task.setStatus(TaskStatus.COMPLETED);
        task.setNextDueDate(DUE.plusWeeks(1));
        when(taskCompletionRepository.findDetailedByIdTaskCompletion(99L)).thenReturn(Optional.of(entry));
        locked(task);
        when(taskCompletionRepository.findLatestByTask(TASK_ID)).thenReturn(Optional.of(entry));
        when(taskCompletionRepository.save(entry)).thenReturn(entry);

        TaskCompletionResponse response = taskCompletionService.undo(99L);

        // The stored due date is what makes this exact rather than a guess.
        assertThat(entry.getIsActive()).isFalse();
        assertThat(savedTask().getNextDueDate()).isEqualTo(DUE);
        assertThat(savedTask().getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(response.getTaskStatus()).isEqualTo(TaskStatus.TODO);
        verify(auditLogService).recordEvent(eq(AuditEntityType.TASK_COMPLETION), eq(99L),
                eq(AuditAction.COMPLETION_UNDONE), any(), any());
    }

    @Test
    void undo_anOlderEntry_isRefused() {
        TaskCompletion older = entry(50L, DUE.minusWeeks(1));
        when(taskCompletionRepository.findDetailedByIdTaskCompletion(50L)).thenReturn(Optional.of(older));
        when(taskRepository.findForUpdateByUid(TASK_UID)).thenReturn(Optional.of(task()));
        when(taskCompletionRepository.findLatestByTask(TASK_ID)).thenReturn(Optional.of(entry(99L, DUE)));

        // Putting the anchor back to an earlier round would leave the schedule behind rounds that
        // already happened.
        assertThatThrownBy(() -> taskCompletionService.undo(50L))
                .isInstanceOf(BusinessRuleException.class);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void undo_always_locksTheTaskBeforeCheckingWhichEntryIsNewest() {
        TaskCompletion entry = entry(99L, DUE);
        when(taskCompletionRepository.findDetailedByIdTaskCompletion(99L)).thenReturn(Optional.of(entry));
        locked(task());
        when(taskCompletionRepository.findLatestByTask(TASK_ID)).thenReturn(Optional.of(entry));
        when(taskCompletionRepository.save(entry)).thenReturn(entry);

        taskCompletionService.undo(99L);

        // A completion landing in between would otherwise make this one the second-newest and move
        // the anchor backwards past it.
        verify(taskRepository).findForUpdateByUid(TASK_UID);
    }

    @Test
    void undo_entryAlreadyUndone_isNotFound() {
        TaskCompletion entry = entry(99L, DUE);
        entry.setIsActive(false);
        when(taskCompletionRepository.findDetailedByIdTaskCompletion(99L)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> taskCompletionService.undo(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCompletion_undoneEntry_isNotFound() {
        TaskCompletion entry = entry(99L, DUE);
        entry.setIsActive(false);
        when(taskCompletionRepository.findDetailedByIdTaskCompletion(99L)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> taskCompletionService.getCompletion(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCompletion_found_namesItsTaskByUidRatherThanId() {
        when(taskCompletionRepository.findDetailedByIdTaskCompletion(99L))
                .thenReturn(Optional.of(entry(99L, DUE)));

        TaskCompletionResponse response = taskCompletionService.getCompletion(99L);

        assertThat(response.getTaskUid()).isEqualTo(TASK_UID);
        assertThat(response.getDueDate()).isEqualTo(DUE);
    }

    private TaskCompletion entry(Long id, LocalDateTime dueDate) {
        return TaskCompletion.builder().idTaskCompletion(id).task(task()).dueDate(dueDate)
                .outcome(TaskOutcome.DONE).isActive(true)
                .attributedTo(member(CALLER_MEMBER_ID))
                .dateCompleted(LocalDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private TaskHistoryQueryRequest query(String cursor, int limit) {
        TaskHistoryQueryRequest q = new TaskHistoryQueryRequest();
        q.setCursor(cursor);
        q.setLimit(limit);
        return q;
    }
    @Test
    void complete_roundNotDueYet_isRefused() {
        Task ahead = task();
        ahead.setNextDueDate(LocalDateTime.now(ZoneOffset.UTC).plusDays(3));
        lockedWithoutSaving(ahead);
        callerIsMember();

        assertThatThrownBy(() -> taskCompletionService.complete(TASK_UID, new CompleteTaskRequest(), CALLER_USER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not due yet");

        // This is also what stops a double tap recording two rounds: the first one rolls the
        // schedule into the future, so the second finds nothing due.
        verify(taskCompletionRepository, never()).save(any(TaskCompletion.class));
    }

    @Test
    void complete_roundDueLaterTheSameDay_isAllowed() {
        Task ahead = task();
        ahead.setNextDueDate(LocalDateTime.now(ZoneOffset.UTC).toLocalDate().atTime(23, 59));
        ahead.setRecurrenceType(RecurrenceType.WEEKLY);
        locked(ahead);
        callerIsMember();
        completionSaves();
        when(recurrenceSchedule.rollForward(any(), any(), any(), any()))
                .thenReturn(LocalDateTime.now(ZoneOffset.UTC).plusDays(7));

        taskCompletionService.complete(TASK_UID, new CompleteTaskRequest(), CALLER_USER_ID);

        // Compared by date, not by instant. A due date carries the time of day the chore was created
        // at, so comparing instants would leave a chore due today unrecordable until that hour.
        assertThat(saved().getOutcome()).isEqualTo(TaskOutcome.DONE);
    }

    @Test
    void skip_roundNotDueYet_isRefused() {
        Task ahead = task();
        ahead.setNextDueDate(LocalDateTime.now(ZoneOffset.UTC).plusDays(3));
        lockedWithoutSaving(ahead);
        callerIsMember();

        // Skipping says a round went by and nobody did it, which cannot be true of one still ahead.
        assertThatThrownBy(() -> taskCompletionService.skip(TASK_UID, new SkipTaskRequest(), CALLER_USER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not due yet");
    }
    @Test
    void complete_choreNeedingProofWithNoPhoto_isRefused() {
        Task needsProof = task();
        needsProof.setRequiresPhotoProof(true);
        lockedWithoutSaving(needsProof);
        callerIsMember();

        assertThatThrownBy(() ->
                taskCompletionService.complete(TASK_UID, completeRequest(null), CALLER_USER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("needs a photo");
        verify(taskCompletionRepository, never()).save(any());
    }

    @Test
    void complete_choreNeedingProofWithAPhoto_isRecorded() {
        Task needsProof = task();
        needsProof.setRequiresPhotoProof(true);
        locked(needsProof);
        callerIsMember();
        completionSaves();
        CompleteTaskRequest request = completeRequest(null);
        request.setPhotoUid("upl-1");
        when(uploadService.attachCompletionPhoto("upl-1", needsProof, CALLER_USER_ID))
                .thenReturn("upl-1");

        taskCompletionService.complete(TASK_UID, request, CALLER_USER_ID);

        assertThat(saved().getCompletionPhotoUid()).isEqualTo("upl-1");
    }

    @Test
    void skip_choreNeedingProof_isAllowedWithoutOne() {
        Task needsProof = task();
        needsProof.setRequiresPhotoProof(true);
        locked(needsProof);
        callerIsMember();
        completionSaves();

        taskCompletionService.skip(TASK_UID, new SkipTaskRequest(), CALLER_USER_ID);

        // A skip asserts the work did not happen, so demanding a picture of it is incoherent.
        assertThat(saved().getOutcome()).isEqualTo(TaskOutcome.SKIPPED);
    }

    @Test
    void undo_always_releasesTheProofBehindTheRound() {
        TaskCompletion newest = entry(7L, DUE);
        newest.setCompletionPhotoUid("upl-1");
        when(taskCompletionRepository.findDetailedByIdTaskCompletion(7L))
                .thenReturn(Optional.of(newest));
        when(taskRepository.findForUpdateByUid(TASK_UID)).thenReturn(Optional.of(task()));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));
        when(taskCompletionRepository.findLatestByTask(TASK_ID)).thenReturn(Optional.of(newest));

        taskCompletionService.undo(7L);

        // An undone round is unreachable, so its proof must not stay a paid-for object.
        verify(uploadService).releaseCompletionPhoto("upl-1");
    }
}
