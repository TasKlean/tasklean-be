package com.tasklean.api.domain.auditlog.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

/**
 * What a caller may ask of an audit feed. Bound from the query string as an object rather than as
 * loose parameters, so {@code @Valid} rejects an over-long cursor or an absurd page as a 400 - a
 * constraint on a bare {@code @RequestParam} would surface as a 500 instead.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogQueryRequest {

    /** The page size when the caller asks for none. */
    public static final int DEFAULT_LIMIT = 20;

    /** Keeps only entries about this kind of entity, e.g. {@code TASK}. */
    @Size(max = FieldLimits.UID)
    private String entityType;

    /**
     * Keeps only entries about this one entity, identified the way the API addresses it. Needs
     * {@code entityType} alongside it: the same identifier means different rows per type.
     */
    @Size(max = FieldLimits.UID)
    private String entityRef;

    /**
     * Keeps only entries this user acted on, named by their user uid - the same {@code uid} the
     * entries' own {@code actor} carries. A plain member may name only themselves; filtering a
     * group's trail by anybody else needs group admin.
     */
    @Size(max = FieldLimits.UID)
    private String actorRef;

    /**
     * Keeps only entries recorded at or after this instant, inclusive. Must carry a zone offset
     * ({@code 2026-11-01T00:00:00+01:00}): the trail is stored in UTC, so a bare local time would
     * shift the range by the caller's own offset without failing.
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime dateFrom;

    /**
     * Keeps only entries recorded before this instant, exclusive - to cover a whole day, name the
     * start of the next one. Same format as {@code dateFrom}.
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime dateTo;

    /** The previous page's {@code nextCursor}. Omitted, the feed starts at the newest entry. */
    @Size(max = FieldLimits.CURSOR)
    private String cursor;

    /** How many entries to return. Omitted, a page holds {@value #DEFAULT_LIMIT}. */
    @Min(1)
    @Max(FieldLimits.PAGE_SIZE)
    private Integer limit = DEFAULT_LIMIT;
}
