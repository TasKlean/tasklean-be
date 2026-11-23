package com.tasklean.api.domain.task;

/** The orderings the task board offers. */
public enum TaskSort {

    /** Soonest due first, undated chores last. The board default. */
    DUE_ASC,

    /** Latest due first, undated chores still last. */
    DUE_DESC,

    /**
     * HIGH, then MEDIUM, then LOW, then the chores nobody prioritised - an ordering the stored
     * string cannot give alphabetically.
     */
    PRIORITY_HIGH_FIRST,

    /** Newest chore first. */
    CREATED_DESC,

    /** By name, A to Z. */
    NAME_ASC
}
