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

    // The entity model addressed as strings, so a rename is caught here rather than at runtime:
    // a mistyped path compiles and then fails on the first query that walks it.
    private static final String ACTION = "action";
    private static final String ACTOR_USER = "actorUser";
    private static final String DATE_CREATED = "dateCreated";
    private static final String ENTITY_ID = "entityId";
    private static final String ENTITY_TYPE = "entityType";
    private static final String GROUP = "group";
    private static final String ID_AUDIT_LOG = "idAuditLog";
    private static final String ID_GROUP = "idGroup";
    private static final String ID_USER = "idUser";

    private AuditLogSpecs() {
    }

    /**
     * Entries belonging to one group.
     *
     * @param groupId the group id
     * @return the specification
     */
    public static Specification<AuditLog> inGroup(Long groupId) {
        return (root, query, cb) -> cb.equal(root.get(GROUP).get(ID_GROUP), groupId);
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
                cb.equal(root.get(ENTITY_TYPE), entityType),
                cb.equal(root.get(ENTITY_ID), entityId));
    }

    /**
     * Entries about one kind of entity.
     *
     * @param entityType the audited entity type
     * @return the specification
     */
    public static Specification<AuditLog> ofEntityType(String entityType) {
        return (root, query, cb) -> cb.equal(root.get(ENTITY_TYPE), entityType);
    }

    /**
     * Entries one user is the actor of.
     *
     * @param userId the acting user's id
     * @return the specification
     */
    public static Specification<AuditLog> byActor(Long userId) {
        return (root, query, cb) -> cb.equal(root.get(ACTOR_USER).get(ID_USER), userId);
    }

    /**
     * Entries recorded at or after an instant - the inclusive end of a date range.
     *
     * @param from the earliest instant to include, already in UTC
     * @return the specification
     */
    public static Specification<AuditLog> recordedFrom(LocalDateTime from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get(DATE_CREATED), from);
    }

    /**
     * Entries recorded before an instant - the exclusive end of a date range. Exclusive so that
     * adjacent ranges cannot both claim an entry, and so a bound never has to name a fraction of
     * a second to cover the whole of one.
     *
     * @param to the instant to stop short of, already in UTC
     * @return the specification
     */
    public static Specification<AuditLog> recordedBefore(LocalDateTime to) {
        return (root, query, cb) -> cb.lessThan(root.get(DATE_CREATED), to);
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
                    cb.equal(creation.get(GROUP).get(ID_GROUP), groupId),
                    cb.equal(creation.get(ACTION), AuditAction.CREATE.name()),
                    cb.equal(creation.get(ACTOR_USER).get(ID_USER), userId),
                    cb.equal(creation.get(ENTITY_TYPE), root.get(ENTITY_TYPE)),
                    cb.equal(creation.get(ENTITY_ID), root.get(ENTITY_ID)));

            return cb.or(cb.equal(root.get(ACTOR_USER).get(ID_USER), userId), cb.exists(created));
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
                cb.lessThan(root.get(DATE_CREATED), dateCreated),
                cb.and(cb.equal(root.get(DATE_CREATED), dateCreated),
                        cb.lessThan(root.get(ID_AUDIT_LOG), id)));
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
                root.fetch(ACTOR_USER, JoinType.LEFT);
            }
            return null;
        };
    }
}
