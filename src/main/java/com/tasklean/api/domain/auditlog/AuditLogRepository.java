package com.tasklean.api.domain.auditlog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByGroupIdGroupOrderByDateCreatedDesc(Long groupId);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByDateCreatedDesc(String entityType, Long entityId);

    List<AuditLog> findByGroupMemberIdGroupMemberOrderByDateCreatedDesc(Long groupMemberId);
}
