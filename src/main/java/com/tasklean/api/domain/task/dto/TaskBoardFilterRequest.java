package com.tasklean.api.domain.task.dto;

import com.tasklean.api.common.validation.FieldLimits;
import com.tasklean.api.domain.task.TaskPriority;
import com.tasklean.api.domain.task.TaskSort;
import com.tasklean.api.domain.task.TaskStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

/**
 * What a caller may ask of a household's task board. Bound from the query string as an object
 * rather than as loose parameters, so {@code @Valid} rejects an over-long search or an absurd page
 * size as a 400 — a constraint on a bare {@code @RequestParam} would surface as a 500 instead.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskBoardFilterRequest {

    /** Which page to return, counting from zero. */
    @Min(0)
    private Integer page = 0;

    /** How many tasks per page. */
    @Min(1)
    @Max(FieldLimits.PAGE_SIZE)
    private Integer size = FieldLimits.TASK_PAGE_SIZE;

    /** The states to keep, repeatable. Naming none is the open ones — the board is a to-do list. */
    private List<TaskStatus> status;

    /** The membership uid of whoever the chores are for. Refused together with {@code unassigned}. */
    @Size(max = FieldLimits.UID)
    private String assignedToUid;

    /** True for the household pool — the chores nobody has taken. */
    private Boolean unassigned;

    private Long categoryId;

    /** Keeps the tasks carrying this tag. A deleted tag matches nothing. */
    private Long tagId;

    /** The priorities to keep, repeatable. Naming none keeps them all. */
    private List<TaskPriority> priority;

    /** True for the chores past their due date, false for the ones that are not. */
    private Boolean overdue;

    /** Matched case-insensitively against a task's name. */
    @Size(max = FieldLimits.SEARCH_TEXT)
    private String q;

    /** The ordering to apply. Omitted, the soonest due comes first. */
    private TaskSort sort = TaskSort.DUE_ASC;
}
