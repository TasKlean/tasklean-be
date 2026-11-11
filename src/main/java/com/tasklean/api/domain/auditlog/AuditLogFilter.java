package com.tasklean.api.domain.auditlog;

import com.tasklean.api.common.exception.InvalidRequestException;
import com.tasklean.api.domain.auditlog.dto.AuditLogQueryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Turns what a caller asked of an audit feed into the predicate that reads it. Its own component so
 * a new filter is a method here rather than another parameter threaded through the service and the
 * repository - the scope and the visibility rule it composes with stay untouched.
 */
@Component
@RequiredArgsConstructor
class AuditLogFilter {

    private final AuditEntityRefs auditEntityRefs;

    /**
     * Narrows a feed by what the request asked for.
     *
     * @param scope  the feed being read - one group's trail, or one account's history
     * @param query  the request's filter parameters
     * @param rights what this caller may narrow the feed by
     * @return the scope narrowed by every filter the caller sent
     * @throws InvalidRequestException if a filter is unusable as sent
     * @throws AccessDeniedException   if a filter is not this caller's to ask for
     */
    public Specification<AuditLog> narrow(Specification<AuditLog> scope, AuditLogQueryRequest query,
                                          AuditFilterRights rights) {
        return byDateRange(byEntity(byActor(scope, query, rights), query), query);
    }

    private Specification<AuditLog> byActor(Specification<AuditLog> scope, AuditLogQueryRequest query,
                                            AuditFilterRights rights) {
        String actorRef = trimmed(query.getActorRef());
        if (actorRef == null) {
            return scope;
        }
        // Refused on the reference, before it is resolved: deciding after the lookup would let a
        // member tell "not your actions" from "no such user" by the status they got back.
        if (!rights.mayFilterByActor(actorRef)) {
            throw new AccessDeniedException("Filtering a group trail by another member needs group admin");
        }

        // A uid naming nobody is an empty feed rather than a 400, as with entityRef - and it reads
        // the same as a uid outside this group, which the scope empties anyway.
        return auditEntityRefs.resolveId(AuditEntityType.USER, actorRef)
                .map(id -> scope.and(AuditLogSpecs.byActor(id)))
                .orElseGet(() -> scope.and(matchesNothing()));
    }

    private Specification<AuditLog> byEntity(Specification<AuditLog> scope, AuditLogQueryRequest query) {
        String entityType = trimmed(query.getEntityType());
        String entityRef = trimmed(query.getEntityRef());

        if (entityType == null) {
            if (entityRef != null) {
                throw new InvalidRequestException("entityRef needs entityType alongside it");
            }
            return scope;
        }
        if (!AuditEntityType.ALL.contains(entityType)) {
            throw new InvalidRequestException("entityType is not a kind of entity this trail records");
        }
        if (entityRef == null) {
            return scope.and(AuditLogSpecs.ofEntityType(entityType));
        }

        // A ref naming nothing is an empty feed, not a 400: the trail outlives what it describes,
        // so an entity that has since been removed is a fair thing to ask the history of.
        Optional<Long> entityId = auditEntityRefs.resolveId(entityType, entityRef);
        return entityId
                .map(id -> scope.and(AuditLogSpecs.aboutEntity(entityType, id)))
                .orElseGet(() -> scope.and(matchesNothing()));
    }

    private Specification<AuditLog> byDateRange(Specification<AuditLog> scope, AuditLogQueryRequest query) {
        LocalDateTime from = utc(query.getDateFrom());
        LocalDateTime to = utc(query.getDateTo());

        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("dateFrom cannot come after dateTo");
        }

        Specification<AuditLog> narrowed = scope;
        if (from != null) {
            narrowed = narrowed.and(AuditLogSpecs.recordedFrom(from));
        }
        if (to != null) {
            narrowed = narrowed.and(AuditLogSpecs.recordedBefore(to));
        }
        return narrowed;
    }

    // The column holds UTC with no zone of its own, so the caller's offset is applied here rather
    // than compared against it - the request states an instant, the column stores one.
    private LocalDateTime utc(OffsetDateTime value) {
        return value != null ? value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime() : null;
    }

    private Specification<AuditLog> matchesNothing() {
        return (root, criteria, cb) -> cb.disjunction();
    }

    private String trimmed(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
