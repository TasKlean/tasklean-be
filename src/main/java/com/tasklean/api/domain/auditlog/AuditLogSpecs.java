package com.tasklean.api.domain.auditlog;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

/**
 * The predicates an audit feed is built from - one per question, composed with {@code and}. They
 * are specifications rather than JPQL because a filter is optional: an absent one contributes no
 * predicate at all, where a query string would have to carry a typed {@code is null} test for every
 * parameter nobody sent.
 */
final class AuditLogSpecs {

    private AuditLogSpecs() {
    }

    /**
     * Entries belonging to one group.
     *
     * @param groupId the group id
     * @return the specification
     */
    public static Specification<AuditLog> inGroup(Long groupId) {
        return (root, query, cb) -> cb.equal(root.get("group").get("idGroup"), groupId);
    }

    /**
     * Entries recorded about one entity - how the account scope reads a single user's history.
     *
     * @param entityType the audited entity type
     * @param entityId   the audited entity id
     * @return the specification
     */
    public static Specification<AuditLog> aboutEntity(String entityType, Long entityId) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("entityType"), entityType),
                cb.equal(root.get("entityId"), entityId));
    }

    /**
     * Entries about one kind of entity.
     *
     * @param entityType the audited entity type
     * @return the specification
     */
    public static Specification<AuditLog> ofEntityType(String entityType) {
        return (root, query, cb) -> cb.equal(root.get("entityType"), entityType);
    }

    /**
     * The entries a plain member of the group may read: the ones they are the actor of, plus every
     * entry about an entity whose {@code CREATE} entry names them.
     *
     * @param groupId the group whose trail is being read
     * @param userId  the reading member's user id
     * @return the specification
     */
    public static Specification<AuditLog> visibleToMember(Long groupId, Long userId) {
        return (root, query, cb) -> {
            Subquery<Long> created = query.subquery(Long.class);
            var creation = created.from(AuditLog.class);
            // Correlated on the outer row's entity, so the subquery asks "did this caller create
            // the thing this entry is about" - the trail is the only record of who created what.
            created.select(cb.literal(1L)).where(
                    cb.equal(creation.get("group").get("idGroup"), groupId),
                    cb.equal(creation.get("action"), AuditAction.CREATE.name()),
                    cb.equal(creation.get("actorUser").get("idUser"), userId),
                    cb.equal(creation.get("entityType"), root.get("entityType")),
                    cb.equal(creation.get("entityId"), root.get("entityId")));

            return cb.or(cb.equal(root.get("actorUser").get("idUser"), userId), cb.exists(created));
        };
    }

    /**
     * Entries older than the cursor's position, by the feed's sort key.
     *
     * @param dateCreated the timestamp to resume after
     * @param id          the row id to resume after, read only when the timestamp ties
     * @return the specification
     */
    public static Specification<AuditLog> after(LocalDateTime dateCreated, Long id) {
        return (root, query, cb) -> cb.or(
                cb.lessThan(root.get("dateCreated"), dateCreated),
                cb.and(cb.equal(root.get("dateCreated"), dateCreated),
                        cb.lessThan(root.get("idAuditLog"), id)));
    }

    /**
     * Fetches the actor with the row, since the response renders their name rather than their id.
     *
     * @return the specification, which adds no predicate of its own
     */
    public static Specification<AuditLog> withActorFetched() {
        return (root, query, cb) -> {
            // Not on the count query: a fetch join there leaves the fetched association with no
            // owner in the selection and Hibernate rejects the statement.
            if (!Long.class.equals(query.getResultType())) {
                root.fetch("actorUser", JoinType.LEFT);
            }
            return null;
        };
    }
}
