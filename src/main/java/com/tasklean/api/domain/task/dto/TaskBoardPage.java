package com.tasklean.api.domain.task.dto;

import lombok.*;

import java.util.List;

/**
 * One page of a household's task board. Its own shape rather than Spring's {@code Page}, whose JSON
 * carries a dozen fields a client never reads and changes between Spring versions.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskBoardPage {

    /** The tasks on this page, in the order asked for. */
    private List<TaskResponse> items;

    /** Which page this is, counting from zero. */
    private int page;

    /** How many tasks a full page holds. */
    private int size;

    /** How many pages the filter yields in total. Zero when nothing matched. */
    private int totalPages;

    /** How many tasks match the filter across every page. */
    private long totalTasks;
}
