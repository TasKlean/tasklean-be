package com.tasklean.api.domain.auditlog;

import com.tasklean.api.domain.auditlog.dto.AuditLogResponse;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.groupmember.GroupMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The audit trail. Records domain-mutation events and exposes group-, entity-, and
 * member-scoped queries over the persisted records (newest first).
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AuditContext auditContext;

    /**
     * Records an audit entry for a domain or account event. Runs in the caller's transaction,
     * so the entry is persisted only if the surrounding operation commits. The acting user,
     * acting group member, and caller IP are resolved from the current request context.
     *
     * @param entityType the audited entity type (use {@link AuditEntityType} constants)
     * @param entityId   the id of the affected entity (the user, for auth/account events)
     * @param action     the action performed
     * @param message    a human-readable description of what happened
     * @param group      the group the entity belongs to; used to attribute the acting group
     *                   member and scope the entry. Pass {@code null} for group-less events
     *                   (e.g. authentication), which leaves both group and group member unset.
     */
    @Transactional
    public void record(String entityType, Long entityId, AuditAction action, String message, Group group) {
        GroupMember actor = group != null ? auditContext.currentActor(group.getIdGroup()) : null;

        AuditLog entry = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action.name())
                .actionMessage(message)
                .ipAddress(auditContext.currentIpAddress())
                .actorUser(auditContext.currentUser())
                .groupMember(actor)
                .group(group)
                .build();

        auditLogRepository.save(entry);
    }

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
