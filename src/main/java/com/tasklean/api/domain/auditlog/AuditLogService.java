package com.tasklean.api.domain.auditlog;

import com.tasklean.api.domain.auditlog.dto.AuditLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read-only access to the audit trail. Exposes group-, entity-, and member-scoped
 * queries over persisted audit-log records (newest first); records are written elsewhere.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Returns all audit-log entries for a group, newest first.
     *
     * @param groupId the group id
     * @return the group's audit-log entries
     */
    public List<AuditLogResponse> getLogsByGroup(Long groupId) {
        return auditLogRepository.findByGroupIdGroupOrderByDateCreatedDesc(groupId).stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    /**
     * Returns audit-log entries for a specific entity, newest first.
     *
     * @param entityType the entity type (e.g. "task", "group")
     * @param entityId   the entity id
     * @return the matching audit-log entries
     */
    public List<AuditLogResponse> getLogsByEntity(String entityType, Long entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByDateCreatedDesc(entityType, entityId).stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    /**
     * Returns audit-log entries recorded for a specific group member, newest first.
     *
     * @param groupMemberId the group member id
     * @return the member's audit-log entries
     */
    public List<AuditLogResponse> getLogsByMember(Long groupMemberId) {
        return auditLogRepository.findByGroupMemberIdGroupMemberOrderByDateCreatedDesc(groupMemberId).stream()
                .map(AuditLogResponse::from)
                .toList();
    }
}
