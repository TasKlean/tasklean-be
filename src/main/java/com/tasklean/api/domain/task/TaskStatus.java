package com.tasklean.api.domain.task;

import java.util.Set;

/**
 * Where a task stands. Stored as the enum name in {@code task.status}, which is CHECK-constrained
 * to the same values. No caller ever sends one: a task is created {@code TODO}, completing it is
 * what makes it {@code COMPLETED}, and archiving has its own endpoint — so a request that could
 * set this would let somebody mark a chore done without recording who did it.
 */
public enum TaskStatus {

    // Waiting to be done. Overdue is not a status of its own: it is next_due_date against now.
    TODO,

    // Started but not finished. Kept for the column's CHECK constraint, deliberately unexposed -
    // nobody marks mopping as begun.
    IN_PROGRESS,

    // Finished, and only ever meaningful for a one-off task: a recurring one stays TODO and rolls
    // its due date on instead.
    COMPLETED,

    // Deliberately retired. Off every board, but its past instances still count toward a share,
    // which is what separates archiving from the soft delete on is_active.
    ARCHIVED;

    /** The states a task is still waiting to be done in — one definition, for every caller. */
    public static final Set<TaskStatus> OPEN = Set.of(TODO, IN_PROGRESS);
}
