package com.tasklean.api.domain.auditlog;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.dto.AuditLogResponse;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The audit trail. Records domain-mutation events and exposes the two read scopes over them
 * (newest first): one group's trail, and the caller's own account history.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AuditContext auditContext;
    private final AuditEntityRefs auditEntityRefs;
    private final AuditLogVisibility auditLogVisibility;
    private final GroupRepository groupRepository;

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
    public void recordEvent(String entityType, Long entityId, AuditAction action, String message, Group group) {
        GroupMember actor = group != null ? auditContext.currentActor(group.getIdGroup()) : null;

        AuditLog entry = AuditLog.builder()
                .uid(UUID.randomUUID().toString())
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
     * Records a self-service account event (register, login, logout, email verified, password
     * reset, …) on a {@code USER} entity. These run on anonymous endpoints, so the security context
     * has no caller — the actor is set explicitly to the subject so {@code actor_user_id} is
     * populated and the per-user index is usable.
     *
     * @param userId the subject user (also the actor)
     * @param action the action performed
     * @param message a human-readable description of what happened
     */
    @Transactional
    public void recordAccountEvent(Long userId, AuditAction action, String message) {
        AuditLog entry = AuditLog.builder()
                .uid(UUID.randomUUID().toString())
                .entityType(AuditEntityType.USER)
                .entityId(userId)
                .action(action.name())
                .actionMessage(message)
                .ipAddress(auditContext.currentIpAddress())
                .actorUser(auditContext.userReference(userId))
                .build();

        auditLogRepository.save(entry);
    }

    /**
     * Returns one group's trail, newest first, narrowed to what the caller may see.
     *
     * @param groupUid the group's public UID
     * @param caller   the authenticated caller
     * @return the visible audit-log entries
     * @throws ResourceNotFoundException if no active group has that UID
     */
    public List<AuditLogResponse> getGroupLogs(String groupUid, AuthPrincipal caller) {
        Group group = groupRepository.findByUidAndIsActiveTrue(groupUid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));

        return toResponses(auditLogVisibility.inGroup(group.getIdGroup(), caller));
    }

    /**
     * Returns the caller's own account history, newest first - the group-less events recorded
     * about their account: sign-ins, credential changes, two-factor and device events.
     *
     * @param userId the caller's user id
     * @return the account-scoped audit-log entries
     */
    public List<AuditLogResponse> getAccountLogs(Long userId) {
        // Keyed on the entity, not the actor: an event another person caused on this account
        // (a role grant, say) is still this account's history.
        return toResponses(auditLogRepository
                .findByEntityTypeAndEntityIdOrderByDateCreatedDesc(AuditEntityType.USER, userId));
    }

    // One ref lookup for the whole page, then a plain mapping - a per-row resolve would be a query
    // per entry, and an audit feed is read far more often than it is written.
    private List<AuditLogResponse> toResponses(List<AuditLog> entries) {
        Map<AuditEntityRefs.EntityKey, String> refs = auditEntityRefs.resolve(entries);
        return entries.stream()
                .map(entry -> AuditLogResponse.from(entry,
                        refs.get(new AuditEntityRefs.EntityKey(entry.getEntityType(), entry.getEntityId()))))
                .toList();
    }
}
