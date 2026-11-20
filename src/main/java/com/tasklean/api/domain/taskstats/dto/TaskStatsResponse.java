package com.tasklean.api.domain.taskstats.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class TaskStatsResponse {

    private String groupUid;

    /** The window actually used, in UTC - the caller's range, or the current week by default. */
    private LocalDateTime from;

    private LocalDateTime to;

    /** Every active member, plus any former one with work in the window. Busiest first. */
    private List<MemberShareResponse> members;

    /** The sum of the rows, so a share of the total needs no second call. */
    private long totalMinutes;

    private long totalRounds;
}
