package com.tasklean.api.domain.taskcompletion;

import com.tasklean.api.config.TaskSweepProperties;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.task.RecurrenceSchedule;
import com.tasklean.api.domain.task.RecurrenceType;
import com.tasklean.api.domain.task.Task;
import com.tasklean.api.domain.task.TaskRepository;
import com.tasklean.api.domain.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskMissSweeperTest {

    private static final String TASK_UID = "tsk-1";
    private static final Long TASK_ID = 44L;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskCompletionRepository taskCompletionRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Spy
    private Clock clock = Clock.systemUTC();

    private final RecurrenceSchedule recurrenceSchedule = new RecurrenceSchedule();
    private final TaskSweepProperties properties = new TaskSweepProperties();

    private TaskMissSweeper sweeper;

    // Built by hand rather than @InjectMocks: the schedule arithmetic and the properties are the
    // real ones, since mocking them would leave the sweep's own rules untested.
    @BeforeEach
    void setUp() {
        sweeper = new TaskMissSweeper(taskRepository, taskCompletionRepository, recurrenceSchedule,
                properties, clock, transactionManager);
    }

    // ------------------------------------------------------------------ what gets written off

    @Test
    void sweepMissedRounds_roundWhoseDayHasPassed_isRecordedAsMissed() {
        LocalDateTime due = today().minusDays(1);
        workListIs(TASK_UID);
        locked(recurring(due, member(7L)));

        sweeper.sweepMissedRounds();

        TaskCompletion written = savedCompletion();
        assertThat(written.getOutcome()).isEqualTo(TaskOutcome.MISSED);
        assertThat(written.getDueDate()).isEqualTo(due);
        // Attributed to whoever was on the hook, with no minutes: the work did not happen.
        assertThat(written.getAttributedTo().getIdGroupMember()).isEqualTo(7L);
        assertThat(written.getMinutes()).isNull();
    }

    @Test
    void sweepMissedRounds_unassignedChore_isAttributedToNobody() {
        workListIs(TASK_UID);
        locked(recurring(today().minusDays(1), null));

        sweeper.sweepMissedRounds();

        // A miss on a chore nobody was holding belongs to no one in particular.
        assertThat(savedCompletion().getAttributedTo()).isNull();
    }

    @Test
    void sweepMissedRounds_always_rollsTheAnchorPastTheMissedRound() {
        workListIs(TASK_UID);
        Task task = recurring(today().minusDays(1), member(7L));
        locked(task);

        sweeper.sweepMissedRounds();

        // Left where it was, the same round would be written off again every run.
        assertThat(task.getNextDueDate()).isAfterOrEqualTo(today());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void sweepMissedRounds_choreNeglectedForDays_recordsOneRoundEach() {
        workListIs(TASK_UID);
        locked(recurring(today().minusDays(4), member(7L)));

        sweeper.sweepMissedRounds();

        // Four days of neglect must not read as one missed day, or the share understates it.
        verify(taskCompletionRepository, times(4)).save(any());
    }

    @Test
    void sweepMissedRounds_choreNeglectedBeyondTheCap_stopsAtItAndLeavesTheRestForLaterRuns() {
        properties.setMaxRoundsPerTask(3);
        workListIs(TASK_UID);
        Task task = recurring(today().minusDays(10), member(7L));
        locked(task);

        sweeper.sweepMissedRounds();

        verify(taskCompletionRepository, times(3)).save(any());
        // Still behind, so the next run picks it up rather than one transaction writing ten rows.
        assertThat(task.getNextDueDate().toLocalDate()).isBefore(LocalDate.now(ZoneOffset.UTC));
    }

    // ------------------------------------------------------------------ what is left alone

    @Test
    void sweepMissedRounds_roundDueToday_isLeftAlone() {
        workListIs(TASK_UID);
        locked(recurring(today().plusHours(23), member(7L)));

        sweeper.sweepMissedRounds();

        // Recordable for the whole of its due date - the same date comparison the guard uses, so
        // comparing instants here would write off a chore still due this evening.
        verify(taskCompletionRepository, never()).save(any());
    }

    @Test
    void sweepMissedRounds_graceDays_keepsARoundRecordableThatMuchLonger() {
        properties.setGraceDays(2);
        workListIs(TASK_UID);
        locked(recurring(today().minusDays(1), member(7L)));

        sweeper.sweepMissedRounds();

        verify(taskCompletionRepository, never()).save(any());
    }

    @Test
    void sweepMissedRounds_choreRecordedBetweenTheWorkListAndTheLock_writesNothing() {
        workListIs(TASK_UID);
        // Somebody completed it, so the anchor has already moved into the future.
        locked(recurring(today().plusDays(6), member(7L)));

        sweeper.sweepMissedRounds();

        verify(taskCompletionRepository, never()).save(any());
    }

    @Test
    void sweepMissedRounds_anchorSittingOnARoundAlreadyRecorded_stepsPastItWithoutWriting() {
        workListIs(TASK_UID);
        Task task = recurring(today().minusDays(1), member(7L));
        locked(task);
        when(taskCompletionRepository.existsByTaskIdTaskAndDueDateAndIsActiveTrue(eq(TASK_ID), any()))
                .thenReturn(true);

        sweeper.sweepMissedRounds();

        // Writing anyway would breach uq_tc_task_due and cost the whole chore's sweep.
        verify(taskCompletionRepository, never()).save(any());
        assertThat(task.getNextDueDate()).isAfterOrEqualTo(today());
    }

    @Test
    void sweepMissedRounds_choreVanishingBeforeTheLock_isSkipped() {
        workListIs(TASK_UID);
        when(taskRepository.findForUpdateByUid(TASK_UID)).thenReturn(Optional.empty());

        sweeper.sweepMissedRounds();

        verify(taskCompletionRepository, never()).save(any());
    }

    @Test
    void sweepMissedRounds_nothingDue_readsNothingFurther() {
        when(taskRepository.findUidsWithRoundPast(any(), any(), any())).thenReturn(List.of());

        sweeper.sweepMissedRounds();

        verify(taskRepository, never()).findForUpdateByUid(any());
    }

    @Test
    void sweepMissedRounds_oneChoreFailing_doesNotStopTheRest() {
        when(taskRepository.findUidsWithRoundPast(any(), any(), any()))
                .thenReturn(List.of("tsk-bad", TASK_UID));
        when(taskRepository.findForUpdateByUid("tsk-bad")).thenThrow(new IllegalStateException("boom"));
        locked(recurring(today().minusDays(1), member(7L)));
        transactionsRunInline();

        sweeper.sweepMissedRounds();

        // Each chore commits on its own, so one bad row costs that chore and not the run.
        assertThat(savedCompletion().getOutcome()).isEqualTo(TaskOutcome.MISSED);
    }

    // ------------------------------------------------------------------ fixtures

    private void workListIs(String... uids) {
        when(taskRepository.findUidsWithRoundPast(any(), any(), any())).thenReturn(List.of(uids));
        transactionsRunInline();
    }

    // The template is built from the mocked manager, so a begin/commit is a no-op and the callback
    // runs straight through - which is what lets this test the sweep rather than Spring.
    private void transactionsRunInline() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private void locked(Task task) {
        when(taskRepository.findForUpdateByUid(TASK_UID)).thenReturn(Optional.of(task));
    }

    private Task recurring(LocalDateTime nextDue, GroupMember assignee) {
        return Task.builder()
                .idTask(TASK_ID).uid(TASK_UID).name("Bins")
                .status(TaskStatus.TODO)
                .isActive(true)
                .recurrenceType(RecurrenceType.DAILY)
                .nextDueDate(nextDue)
                .assignedTo(assignee)
                .group(Group.builder().idGroup(1L).uid("grp-1").build())
                .build();
    }

    private GroupMember member(Long id) {
        return GroupMember.builder().idGroupMember(id).uid("gm-" + id).build();
    }

    private LocalDateTime today() {
        return LocalDate.now(ZoneOffset.UTC).atStartOfDay();
    }

    private TaskCompletion savedCompletion() {
        ArgumentCaptor<TaskCompletion> saved = ArgumentCaptor.forClass(TaskCompletion.class);
        verify(taskCompletionRepository, atLeastOnce()).save(saved.capture());
        return saved.getValue();
    }
}
