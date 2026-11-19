package com.tasklean.api.domain.task;

/**
 * How much a task matters relative to the household's others. Stored as the enum name in
 * {@code task.priority}, which is CHECK-constrained to the same values.
 */
public enum TaskPriority {

    HIGH,

    MEDIUM,

    LOW
}
