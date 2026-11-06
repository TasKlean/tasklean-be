package com.tasklean.api.domain.auditlog;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.domain.user.UserRole;
import com.tasklean.api.security.GroupSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Decides which of a group's audit entries a caller may read, and fetches exactly those. An admin
 * reads the whole trail; a plain member reads their own actions plus the history of everything they
 * created. This is row-level, so it belongs in the query - a {@code @PreAuthorize} boolean can only
 * answer yes or no to the endpoint, never narrow what comes back.
 */
@Component
@RequiredArgsConstructor
public class AuditLogVisibility {

    private final AuditLogRepository auditLogRepository;
    private final GroupSecurity groupSecurity;

    /**
     * Fetches the entries of one group that the caller is allowed to see, newest first.
     *
     * @param groupId the group whose trail is being read
     * @param caller  the authenticated caller, already known by the endpoint's
     *                {@code @PreAuthorize} to belong to the group or to hold a platform role
     * @return the visible entries, newest first
     */
    public List<AuditLog> inGroup(Long groupId, AuthPrincipal caller) {
        if (readsWholeTrail(groupId, caller)) {
            return auditLogRepository.findByGroupIdGroupOrderByDateCreatedDesc(groupId);
        }
        // What a member created is read off the trail itself, not off the entities: only task
        // carries a created_by column, while a CREATE entry names the actor for every type.
        return auditLogRepository.findInGroupVisibleToMember(groupId, caller.userId(), AuditAction.CREATE.name());
    }

    // Any platform role above USER reads across households - the same span the endpoint's
    // hasRole('ADMIN') already admits through the role hierarchy.
    private boolean readsWholeTrail(Long groupId, AuthPrincipal caller) {
        return caller.role() != UserRole.USER || groupSecurity.isAdminOfGroup(groupId);
    }
}
