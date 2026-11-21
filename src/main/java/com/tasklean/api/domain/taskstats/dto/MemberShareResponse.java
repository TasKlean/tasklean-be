package com.tasklean.api.domain.taskstats.dto;

import com.tasklean.api.domain.groupmember.dto.MemberSummaryResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberShareResponse {

    /** Who did the work, keyed by their membership - which is what the board's filter takes. */
    private MemberSummaryResponse member;

    /** Minutes of work done in the window. Zero for a member who did none. */
    private long minutes;

    /** How many rounds they completed in the window. */
    private long roundsDone;

    /**
     * Whether the membership has ended. A former member appears only when they did work in the
     * window, so the rows still sum to the total; they are never listed at zero.
     */
    private boolean former;
}
