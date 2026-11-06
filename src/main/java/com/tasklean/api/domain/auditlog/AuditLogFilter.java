package com.tasklean.api.domain.auditlog;

import com.tasklean.api.common.exception.InvalidRequestException;
import com.tasklean.api.domain.auditlog.dto.AuditLogQueryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Turns what a caller asked of an audit feed into the predicate that reads it. Its own component so
 * a new filter is a method here rather than another parameter threaded through the service and the
 * repository - the scope and the visibility rule it composes with stay untouched.
 */
@Component
@RequiredArgsConstructor
public class AuditLogFilter {

    private final AuditEntityRefs auditEntityRefs;

    /**
     * Narrows a feed by what the request asked for.
     *
     * @param scope the feed being read - one group's trail, or one account's history
     * @param query the request's filter parameters
     * @return the scope narrowed by every filter the caller sent
     * @throws InvalidRequestException if a filter is unusable as sent
     */
    public Specification<AuditLog> narrow(Specification<AuditLog> scope, AuditLogQueryRequest query) {
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
