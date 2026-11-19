package com.tasklean.api.domain.taskcompletion;

/**
 * What became of one scheduled instance of a task. Stored as the enum name in
 * {@code task_completion.outcome}.
 */
public enum TaskOutcome {

    // Somebody did it. The only outcome that credits a member's share.
    DONE,

    // Deliberately passed over. Advances the schedule without crediting anyone.
    SKIPPED,

    // Nobody did it before the next instance came due. Written by the sweep, not by a person.
    MISSED
}
