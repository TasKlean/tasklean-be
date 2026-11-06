package com.tasklean.api.domain.auditlog;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/** Data access for the audit trail: one page of a scope's entries by keyset, and its total. */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * One page of a group's entries, newest first.
     * <p>
     * The actor is fetched with the row: the response renders their name, not their id, so without
     * the graph the mapping would depend on a session outliving the service call.
     *
     * @param groupId the group id
     * @param afterTs the timestamp to resume after, or {@code null} for the newest page
     * @param afterId the row id to resume after, read only when {@code afterTs} ties
     * @param limit   how many entries to read
     * @return the page's entries, newest first
     */
    // The cast is load-bearing: a bare "? is null" leaves Postgres with no type for the bind and
    // it refuses the statement, so one query cannot serve the first page and the later ones.
    @EntityGraph(attributePaths = "actorUser")
    @Query("""
            select a from AuditLog a
            where a.group.idGroup = :groupId
              and (cast(:afterTs as LocalDateTime) is null
                   or a.dateCreated < :afterTs
                   or (a.dateCreated = :afterTs and a.idAuditLog < :afterId))
            order by a.dateCreated desc, a.idAuditLog desc
            """)
    List<AuditLog> findInGroup(Long groupId, LocalDateTime afterTs, Long afterId, Limit limit);

    /**
     * How many entries a group's trail holds, pages included.
     *
     * @param groupId the group id
     * @return the total number of entries
     */
    @Query("select count(a) from AuditLog a where a.group.idGroup = :groupId")
    long countInGroup(Long groupId);

    /**
     * One page of a group's entries as a plain member may see them, newest first: the entries they
     * are the actor of, plus every entry about an entity whose {@code CREATE} entry names them.
     * <p>
     * The narrowing is in the query because it is row-level - what a member may read differs per
     * row, which no {@code @PreAuthorize} expression can express.
     *
     * @param groupId      the group whose trail is being read
     * @param userId       the reading member's user id
     * @param createAction {@code AuditAction.CREATE}'s name - passed in so the enum stays the one
     *                     source of the action's spelling rather than a literal inside the query
     * @param afterTs      the timestamp to resume after, or {@code null} for the newest page
     * @param afterId      the row id to resume after, read only when {@code afterTs} ties
     * @param limit        how many entries to read
     * @return the page's visible entries, newest first
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
              and (cast(:afterTs as LocalDateTime) is null
                   or a.dateCreated < :afterTs
                   or (a.dateCreated = :afterTs and a.idAuditLog < :afterId))
            order by a.dateCreated desc, a.idAuditLog desc
            """)
    List<AuditLog> findInGroupVisibleToMember(Long groupId, Long userId, String createAction,
                                             LocalDateTime afterTs, Long afterId, Limit limit);

    /**
     * How many of a group's entries a plain member may see, pages included.
     *
     * @param groupId      the group whose trail is being read
     * @param userId       the reading member's user id
     * @param createAction {@code AuditAction.CREATE}'s name
     * @return the total number of entries visible to that member
     */
    @Query("""
            select count(a) from AuditLog a
            where a.group.idGroup = :groupId
              and (a.actorUser.idUser = :userId
                   or exists (select 1 from AuditLog creation
                              where creation.group.idGroup = :groupId
                                and creation.action = :createAction
                                and creation.actorUser.idUser = :userId
                                and creation.entityType = a.entityType
                                and creation.entityId = a.entityId))
            """)
    long countInGroupVisibleToMember(Long groupId, Long userId, String createAction);

    /**
     * One page of the entries recorded about a single entity, newest first - which is how the
     * account scope reads one user's own history.
     * <p>
     * The actor is fetched with the row: the response renders their name, not their id, so without
     * the graph the mapping would depend on a session outliving the service call.
     *
     * @param entityType the audited entity type
     * @param entityId   the audited entity id
     * @param afterTs    the timestamp to resume after, or {@code null} for the newest page
     * @param afterId    the row id to resume after, read only when {@code afterTs} ties
     * @param limit      how many entries to read
     * @return the page's entries, newest first
     */
    @EntityGraph(attributePaths = "actorUser")
    @Query("""
            select a from AuditLog a
            where a.entityType = :entityType and a.entityId = :entityId
              and (cast(:afterTs as LocalDateTime) is null
                   or a.dateCreated < :afterTs
                   or (a.dateCreated = :afterTs and a.idAuditLog < :afterId))
            order by a.dateCreated desc, a.idAuditLog desc
            """)
    List<AuditLog> findForEntity(String entityType, Long entityId,
                                 LocalDateTime afterTs, Long afterId, Limit limit);

    /**
     * How many entries one entity's history holds, pages included.
     *
     * @param entityType the audited entity type
     * @param entityId   the audited entity id
     * @return the total number of entries
     */
    @Query("select count(a) from AuditLog a where a.entityType = :entityType and a.entityId = :entityId")
    long countForEntity(String entityType, Long entityId);
}
