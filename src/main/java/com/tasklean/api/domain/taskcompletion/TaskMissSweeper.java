package com.tasklean.api.domain.taskcompletion;

import com.tasklean.api.config.TaskSweepProperties;
import com.tasklean.api.domain.task.RecurrenceSchedule;
import com.tasklean.api.domain.task.RecurrenceType;
import com.tasklean.api.domain.task.Task;
import com.tasklean.api.domain.task.TaskRepository;
import com.tasklean.api.domain.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Writes off the rounds nobody did, as soon as each one's day has passed. The round stays
 * recordable: the anchor is left parked on it until its successor comes due, so a chore done late
 * reuses the row written here rather than being refused.
 *
 * <p>Its own component rather than a method on {@link TaskCompletionService} for the same reason as
 * {@code UploadSweeper}: the work is per-task and each task commits on its own, so one chore that
 * cannot be swept costs that chore and not the run.
 */
@Slf4j
@Component
public class TaskMissSweeper {

    // The cadences that can name a next round. A one-off has only the one, and a custom rule is
    // not implemented, so neither ever rolls - they keep their anchor and stay recordable.
    private static final Set<RecurrenceType> ROLLING =
            EnumSet.of(RecurrenceType.DAILY, RecurrenceType.WEEKLY, RecurrenceType.MONTHLY);

    private final TaskRepository taskRepository;
    private final TaskCompletionRepository taskCompletionRepository;
    private final RecurrenceSchedule recurrenceSchedule;
    private final TaskSweepProperties properties;
    private final Clock clock;

    // One transaction per chore, through a template rather than @Transactional: calling an
    // annotated method on this same bean would bypass the proxy and run every sweep untransacted.
    private final TransactionTemplate transactionTemplate;

    public TaskMissSweeper(TaskRepository taskRepository,
                           TaskCompletionRepository taskCompletionRepository,
                           RecurrenceSchedule recurrenceSchedule,
                           TaskSweepProperties properties,
                           Clock clock,
                           PlatformTransactionManager transactionManager) {
        this.taskRepository = taskRepository;
        this.taskCompletionRepository = taskCompletionRepository;
        this.recurrenceSchedule = recurrenceSchedule;
        this.properties = properties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Scheduled write-off — records the rounds whose day has passed, hourly. */
    @Scheduled(fixedRate = 60 * 60 * 1000) // hourly
    public void sweepMissedRounds() {
        List<String> uids = taskRepository.findUidsWithRoundPast(
                cutoff(), TaskStatus.OPEN, ROLLING, Limit.of(properties.getBatch()));
        if (uids.isEmpty()) {
            return;
        }

        int rounds = 0;
        int failed = 0;
        for (String uid : uids) {
            try {
                rounds += transactionTemplate.execute(status -> sweepOne(uid));
            } catch (RuntimeException ex) {
                // The anchor is untouched, so the next run tries this chore again.
                failed++;
                log.warn("Missed-round sweep failed for one chore: uid={}", uid, ex);
            }
        }
        log.info("Missed-round sweep: tasks={} rounds={} failed={}", uids.size(), rounds, failed);
    }

    // Locks the chore first: between the work list and here, somebody may have recorded the very
    // round being written off, which would move the anchor out from under this.
    private int sweepOne(String uid) {
        Task task = taskRepository.findForUpdateByUid(uid).orElse(null);
        if (task == null || !sweepable(task)) {
            return 0;
        }

        int written = 0;
        int steps = 0;
        boolean rolled = false;
        while (steps++ < properties.getMaxRoundsPerTask() && isPast(task.getNextDueDate())) {
            LocalDateTime due = task.getNextDueDate();
            // Already there when the round was recorded before the sweep reached it, or when an
            // admin moved the anchor back onto one. Writing anyway would breach uq_tc_task_due.
            if (!taskCompletionRepository.existsByTaskIdTaskAndDueDateAndIsActiveTrue(
                    task.getIdTask(), due)) {
                taskCompletionRepository.save(missFor(task, due));
                written++;
            }

            LocalDateTime next = nextRound(task, due);
            // The anchor stays on the missed round until its successor comes due. That is what
            // keeps it recordable: a chore done late lands on the round it was actually for,
            // reusing the row written here rather than being refused as not yet due.
            if (next == null || next.toLocalDate().isAfter(LocalDate.now(clock))) {
                break;
            }
            task.setNextDueDate(next);
            rolled = true;
        }

        if (written > 0 || rolled) {
            taskRepository.save(task);
            log.info("Rounds written off: taskUid={} rounds={} nextDue={}",
                    task.getUid(), written, task.getNextDueDate());
        }
        return written;
    }

    // Null when the chore has no next round to roll to, which leaves its anchor where it is and
    // so leaves it recordable for good. A custom rule has no arithmetic yet, and calling for it
    // would throw and cost the chore its whole sweep.
    private LocalDateTime nextRound(Task task, LocalDateTime due) {
        return ROLLING.contains(task.getRecurrenceType())
                ? recurrenceSchedule.next(task.getRecurrenceType(), task.getRecurrencePattern(), due)
                : null;
    }

    // Attributed to whoever was on the hook, and to nobody when the chore was unassigned - a miss
    // then belongs to no one in particular. No minutes: the work did not happen.
    private TaskCompletion missFor(Task task, LocalDateTime due) {
        return TaskCompletion.builder()
                .task(task)
                .attributedTo(task.getAssignedTo())
                .outcome(TaskOutcome.MISSED)
                .dueDate(due)
                .dateCompleted(LocalDateTime.now(clock))
                .isActive(true)
                .build();
    }

    // Re-checked under the lock, since the work list was read without one.
    private boolean sweepable(Task task) {
        return Boolean.TRUE.equals(task.getIsActive())
                && TaskStatus.OPEN.contains(task.getStatus())
                && isPast(task.getNextDueDate());
    }

    // By date, exactly as the recording guard compares it: a chore is recordable for the whole of
    // its due date, so it is written off only once a later day has started. Comparing instants
    // would write off a chore due this evening.
    private boolean isPast(LocalDateTime dueDate) {
        return dueDate != null && dueDate.toLocalDate()
                .isBefore(LocalDate.now(clock).minusDays(properties.getGraceDays()));
    }

    private LocalDateTime cutoff() {
        return LocalDate.now(clock).minusDays(properties.getGraceDays()).atStartOfDay();
    }
}
