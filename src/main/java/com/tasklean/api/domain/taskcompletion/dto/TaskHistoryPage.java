package com.tasklean.api.domain.taskcompletion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * One page of a task's history. Cursor-paged rather than page-numbered, unlike the board: a daily
 * chore accumulates a row a day, and nothing about a history needs a page count.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskHistoryPage {

    /** The instances, most recently recorded first. */
    private List<TaskCompletionResponse> items;

    /** Pass as {@code cursor} to get the next page. {@code null} means this page is the last. */
    private String nextCursor;

    /** How many instances this task has recorded in total, pages included. */
    private long total;
}
