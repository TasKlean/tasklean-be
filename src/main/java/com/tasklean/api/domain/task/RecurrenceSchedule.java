package com.tasklean.api.domain.task;

import com.tasklean.api.common.exception.InvalidRequestException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * When a recurring task is next due. Arithmetic over the task's own due date and nothing else, so
 * it needs no repository and every rule here is provable without a mock.
 *
 * <p>The due date is the schedule's only anchor: a weekly chore recurs on whichever day its due
 * date falls on, which is why there is no day-of-week to store and nothing that can contradict it.
 * Monthly is the one type needing more than the date itself, because a short month would otherwise
 * move the schedule earlier for good.
 */
@Component
public class RecurrenceSchedule {

    /** The one key {@link #anchorFor} writes and {@link #next} reads back. */
    private static final String DAY_OF_MONTH = "dayOfMonth";

    /**
     * The next due date one step on from the given one.
     *
     * @param type    how often the task recurs; a one-off task has no next due date and must not
     *                reach here
     * @param pattern the task's stored recurrence pattern, or null if it has none
     * @param from    the due date to step on from
     * @return the following due date
     * @throws InvalidRequestException if the task declares a custom recurrence, which is not
     *                                 implemented
     */
    public LocalDateTime next(RecurrenceType type, Map<String, Object> pattern, LocalDateTime from) {
        return switch (type) {
            case DAILY -> from.plusDays(1);
            case WEEKLY -> from.plusWeeks(1);
            case MONTHLY -> nextMonthly(pattern, from);
            case CUSTOM -> throw new InvalidRequestException("A custom recurrence is not supported yet");
        };
    }

    /**
     * The first due date after {@code notBefore}, stepping on from {@code from} a period at a time.
     *
     * <p>Counting from the due date rather than from {@code notBefore} is what keeps a schedule
     * fixed: a weekly Monday chore completed on a Thursday is next due the following Monday, not
     * the following Thursday. Stepping repeatedly is what stops a long-neglected task being
     * recorded as due again the moment it is caught up on.
     *
     * @param type      how often the task recurs
     * @param pattern   the task's stored recurrence pattern, or null if it has none
     * @param from      the due date to step on from
     * @param notBefore the instant the answer must fall after, normally now
     * @return the first due date after {@code notBefore}
     * @throws InvalidRequestException if the task declares a custom recurrence
     */
    public LocalDateTime rollForward(RecurrenceType type, Map<String, Object> pattern,
                                     LocalDateTime from, LocalDateTime notBefore) {
        LocalDateTime due = next(type, pattern, from);
        while (!due.isAfter(notBefore)) {
            LocalDateTime following = next(type, pattern, due);
            // Every step above adds a whole period, so this cannot hold - but a step that stood
            // still would spin here forever instead of failing, which is worth refusing outright.
            if (!following.isAfter(due)) {
                throw new IllegalStateException("Recurrence step did not advance from " + due);
            }
            due = following;
        }
        return due;
    }

    /**
     * The recurrence pattern to store for a task, derived from its first due date rather than sent
     * by a caller — so the pattern and the due date cannot disagree about when the task is due.
     *
     * @param type     how often the task recurs
     * @param firstDue the task's first due date
     * @return the pattern to store, or null if the type needs none
     */
    public Map<String, Object> anchorFor(RecurrenceType type, LocalDateTime firstDue) {
        return switch (type) {
            case MONTHLY -> Map.of(DAY_OF_MONTH, firstDue.getDayOfMonth());
            // Daily and weekly need nothing: the due date already says which day. Custom is
            // refused by next(...) rather than anchored here.
            case DAILY, WEEKLY, CUSTOM -> null;
        };
    }

    private LocalDateTime nextMonthly(Map<String, Object> pattern, LocalDateTime from) {
        // plusMonths clamps into a shorter month on its own, so the anchor day has to be put back
        // afterwards: without that, one February would hold a 31st task at the 28th ever after.
        LocalDateTime month = from.plusMonths(1);
        int anchor = anchorDay(pattern, from);
        return month.withDayOfMonth(Math.min(anchor, month.toLocalDate().lengthOfMonth()));
    }

    // A task whose pattern never recorded an anchor keeps recurring on the day it is already due.
    private int anchorDay(Map<String, Object> pattern, LocalDateTime from) {
        Object stored = pattern == null ? null : pattern.get(DAY_OF_MONTH);
        // jsonb comes back as whichever Number the mapper chose, and a value outside a month's
        // range could only be a stored mistake, so it is read as a number and clamped.
        return stored instanceof Number day ? Math.clamp(day.intValue(), 1, 31) : from.getDayOfMonth();
    }
}
