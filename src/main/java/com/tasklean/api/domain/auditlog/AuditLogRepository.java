package com.tasklean.api.domain.auditlog;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * Entries attributed to one membership, newest first.
     * <p>
     * The actor is fetched with the row: the response renders their name, not their id, so without
     * the graph the mapping would depend on a session outliving the service call.
     *
     * @param groupMemberId the acting membership's id
     * @return the matching entries, newest first
     */
    @EntityGraph(attributePaths = "actorUser")
    List<AuditLog> findByGroupMemberIdGroupMemberOrderByDateCreatedDesc(Long groupMemberId);
}
