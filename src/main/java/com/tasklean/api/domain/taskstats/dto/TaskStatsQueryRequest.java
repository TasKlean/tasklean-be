package com.tasklean.api.domain.taskstats.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

@Getter
@Setter
public class TaskStatsQueryRequest {

    /**
     * Counts only rounds done from this instant, inclusive. Needs a zone offset
     * ({@code 2026-11-01T00:00:00+01:00}): rounds are stored in UTC, so a bare local time would
     * shift the window by the caller's own offset without failing.
     *
     * <p>Omitted together with {@code dateTo}, the window is the current UTC week.
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime dateFrom;

    /**
     * Counts only rounds done before this instant, exclusive - to cover a whole day, name the start
     * of the next one. Same format as {@code dateFrom}.
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime dateTo;
}
