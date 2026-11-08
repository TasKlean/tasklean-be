package com.tasklean.api.domain.auditlog;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.InvalidRequestException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.dto.AuditLogPage;
import com.tasklean.api.domain.auditlog.dto.AuditLogQueryRequest;
import com.tasklean.api.domain.auditlog.dto.AuditLogResponse;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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

    // The feed's sort key, and the one the cursor encodes - the row id breaks a tie on the
    // timestamp so two entries written in the same instant cannot repeat across pages.
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Direction.DESC, "dateCreated").and(Sort.by(Sort.Direction.DESC, "idAuditLog"));

    private final AuditLogRepository auditLogRepository;
    private final AuditContext auditContext;
    private final AuditEntityRefs auditEntityRefs;
    private final AuditLogVisibility auditLogVisibility;
    private final AuditLogFilter auditLogFilter;
    private final AuditLogCursor auditLogCursor;
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
        recordEvent(entityType, entityId, action, message, group, null);
    }

    /**
     * Records an audit entry for an update, naming the fields it changed. Behaves as
     * {@link #recordEvent(String, Long, AuditAction, String, Group)} otherwise.
     *
     * @param entityType the audited entity type (use {@link AuditEntityType} constants)
     * @param entityId   the id of the affected entity
     * @param action     the action performed
     * @param message    a human-readable description of what happened
     * @param group      the group the entity belongs to, or {@code null} for group-less events
     * @param changes    the fields the update changed. An empty set writes no entry at all -
     *                   a save that altered nothing is not an event. Pass {@code null} for an
     *                   event that is not a tracked update, which always writes.
     */
    @Transactional
    public void recordEvent(String entityType, Long entityId, AuditAction action, String message,
                            Group group, AuditChanges changes) {
        if (changes != null && changes.isEmpty()) {
            return;
        }
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
                .changedFields(changes != null ? changes.entries() : null)
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
     * Returns one page of a group's trail, newest first, narrowed to what the caller may see.
     *
     * @param groupUid the group's public UID
     * @param caller   the authenticated caller
     * @param query    the page asked for
     * @return the page of visible audit-log entries
     * @throws ResourceNotFoundException if no active group has that UID
     * @throws InvalidRequestException   if the cursor is not one we handed out
     */
    public AuditLogPage getGroupLogs(String groupUid, AuthPrincipal caller, AuditLogQueryRequest query) {
        Group group = groupRepository.findByUidAndIsActiveTrue(groupUid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));

        // No IP addresses on a group trail: an entry is read by every member, and a member's
        // address is theirs, not the household's.
        return page(auditLogFilter.narrow(auditLogVisibility.inGroup(group.getIdGroup(), caller), query), query, null);
    }

    /**
     * Returns one page of the caller's own account history, newest first - the group-less events
     * recorded about their account: sign-ins, credential changes, two-factor and device events.
     *
     * @param userId the caller's user id
     * @param query  the page asked for
     * @return the page of account-scoped audit-log entries
     * @throws InvalidRequestException if the cursor is not one we handed out
     */
    public AuditLogPage getAccountLogs(Long userId, AuditLogQueryRequest query) {
        // Keyed on the entity, not the actor: an event another person caused on this account
        // (a role grant, say) is still this account's history.
        Specification<AuditLog> scope = AuditLogSpecs.aboutEntity(AuditEntityType.USER, userId);

        return page(auditLogFilter.narrow(scope, query), query, userId);
    }

    // One row more than the page holds, so a next cursor means a further entry really exists; the
    // total skips the cursor predicate, being the size of the feed rather than of what is left.
    private AuditLogPage page(Specification<AuditLog> feed, AuditLogQueryRequest query, Long ipVisibleTo) {
        int limit = pageSize(query);
        AuditLogCursor.Position after = auditLogCursor.decode(query.getCursor());
        Specification<AuditLog> onePage = after == null
                ? feed : feed.and(AuditLogSpecs.after(after.dateCreated(), after.id()));

        List<AuditLog> read = auditLogRepository.findBy(onePage.and(AuditLogSpecs.withActorFetched()),
                q -> q.sortBy(NEWEST_FIRST).limit(limit + 1).all());
        List<AuditLog> entries = read.size() > limit ? read.subList(0, limit) : read;

        return AuditLogPage.builder()
                .items(toResponses(entries, ipVisibleTo))
                .nextCursor(read.size() > limit ? auditLogCursor.encode(entries.getLast()) : null)
                .total(auditLogRepository.count(feed))
                .build();
    }

    // "limit=" with nothing after it binds to null, so the default lives here as well as on the
    // request - the field's initializer only covers the parameter being absent altogether.
    private int pageSize(AuditLogQueryRequest query) {
        return query.getLimit() == null ? AuditLogQueryRequest.DEFAULT_LIMIT : query.getLimit();
    }

    // One ref lookup for the whole page, then a plain mapping - a per-row resolve would be a query
    // per entry, and an audit feed is read far more often than it is written.
    private List<AuditLogResponse> toResponses(List<AuditLog> entries, Long ipVisibleTo) {
        Map<AuditEntityRefs.EntityKey, String> refs = auditEntityRefs.resolve(entries);
        return entries.stream()
                .map(entry -> {
                    String ref = refs.get(new AuditEntityRefs.EntityKey(entry.getEntityType(), entry.getEntityId()));
                    return showsIpTo(entry, ipVisibleTo)
                            ? AuditLogResponse.withIpAddress(entry, ref) : AuditLogResponse.from(entry, ref);
                })
                .toList();
    }

    // Per entry, not per feed: an account history also holds what an admin did to the account, and
    // that entry carries the admin's address rather than the reader's.
    private boolean showsIpTo(AuditLog entry, Long ipVisibleTo) {
        return ipVisibleTo != null && entry.getActorUser() != null
                && ipVisibleTo.equals(entry.getActorUser().getIdUser());
    }

}
