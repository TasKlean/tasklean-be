package com.tasklean.api.domain.auditlog.dto;

import lombok.*;

import java.util.List;

/**
 * One page of an audit feed. Its own shape rather than Spring's {@code Page}: that one is built for
 * offset paging and serializes a dozen fields a cursor-paged client cannot use.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogPage {

    /** The entries, newest first. */
    private List<AuditLogResponse> items;

    /** Pass as {@code cursor} to get the next page. {@code null} means this page is the last. */
    private String nextCursor;

    /** How many entries the caller can reach in this feed in total, pages included. */
    private long total;
}
