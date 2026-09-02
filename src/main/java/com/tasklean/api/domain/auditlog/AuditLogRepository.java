package com.tasklean.api.domain.auditlog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access for the audit trail — read-only queries by group, entity, or member. */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByGroupIdGroupOrderByDateCreatedDesc(Long groupId);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByDateCreatedDesc(String entityType, Long entityId);

    List<AuditLog> findByGroupMemberIdGroupMemberOrderByDateCreatedDesc(Long groupMemberId);
}
