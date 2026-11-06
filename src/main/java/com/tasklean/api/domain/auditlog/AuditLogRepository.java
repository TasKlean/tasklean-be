package com.tasklean.api.domain.auditlog;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access for the audit trail — read-only queries by group, entity, or member. */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * A group's entries, newest first.
     * <p>
     * The actor is fetched with the row: the response renders their name, not their id, so without
     * the graph the mapping would depend on a session outliving the service call.
     *
     * @param groupId the group id
     * @return the matching entries, newest first
     */
    @EntityGraph(attributePaths = "actorUser")
    List<AuditLog> findByGroupIdGroupOrderByDateCreatedDesc(Long groupId);

    /**
     * One entity's entries, newest first.
     * <p>
     * The actor is fetched with the row: the response renders their name, not their id, so without
     * the graph the mapping would depend on a session outliving the service call.
     *
     * @param entityType the audited entity type
     * @param entityId   the audited entity id
     * @return the matching entries, newest first
     */
    @EntityGraph(attributePaths = "actorUser")
    List<AuditLog> findByEntityTypeAndEntityIdOrderByDateCreatedDesc(String entityType, Long entityId);

    /**
     * A group's entries as a plain member may see them, newest first: the entries they are the
     * actor of, plus every entry about an entity whose {@code CREATE} entry names them.
     * <p>
     * The narrowing is in the query because it is row-level - what a member may read differs per
     * row, which no {@code @PreAuthorize} expression can express.
     *
     * @param groupId      the group whose trail is being read
     * @param userId       the reading member's user id
     * @param createAction {@code AuditAction.CREATE}'s name - passed in so the enum stays the one
     *                     source of the action's spelling rather than a literal inside the query
     * @return the visible entries, newest first
     */
    @EntityGraph(attributePaths = "actorUser")
    @Query("""
            select a from AuditLog a
            where a.group.idGroup = :groupId
              and (a.actorUser.idUser = :userId
                   or exists (select 1 from AuditLog creation
                              where creation.group.idGroup = :groupId
                                and creation.action = :createAction
                                and creation.actorUser.idUser = :userId
                                and creation.entityType = a.entityType
                                and creation.entityId = a.entityId))
            order by a.dateCreated desc
            """)
    List<AuditLog> findInGroupVisibleToMember(Long groupId, Long userId, String createAction);
}
