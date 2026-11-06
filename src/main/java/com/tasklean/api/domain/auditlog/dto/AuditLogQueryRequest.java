package com.tasklean.api.domain.auditlog.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

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

    /** The previous page's {@code nextCursor}. Omitted, the feed starts at the newest entry. */
    @Size(max = FieldLimits.CURSOR)
    private String cursor;

    /** How many entries to return. Omitted, a page holds {@value #DEFAULT_LIMIT}. */
    @Min(1)
    @Max(FieldLimits.PAGE_SIZE)
    private Integer limit = DEFAULT_LIMIT;
}
