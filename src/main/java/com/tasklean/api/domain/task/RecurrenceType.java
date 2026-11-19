package com.tasklean.api.domain.task;

/**
 * How often a task comes due. Stored as the enum name in {@code task.recurrence_type}, which is
 * CHECK-constrained to the same values. A null column means the task happens once.
 */
public enum RecurrenceType {

    // Every day.
    DAILY,

    // Every seven days, on whichever day its due date falls on - so there is no day-of-week to set.
    WEEKLY,

    // Every month, on the day of month the first due date fell on, clamped in a shorter month.
    MONTHLY,

    // A pattern of the caller's own. Not implemented: RecurrenceSchedule refuses it.
    CUSTOM
}
