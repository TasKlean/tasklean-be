package com.tasklean.api.domain.auditlog;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.domain.user.UserRole;
import com.tasklean.api.security.GroupSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Decides which of a group's audit entries a caller may read. An admin reads the whole trail; a
 * plain member reads their own actions plus the history of everything they created. This is
 * row-level, so it contributes a predicate rather than a verdict - a {@code @PreAuthorize} boolean
 * can only answer yes or no to the endpoint, never narrow what comes back.
 */
@Component
@RequiredArgsConstructor
class AuditLogVisibility {

    private final GroupSecurity groupSecurity;

    /**
     * The predicate restricting a group's trail to what the caller may see.
     *
     * @param groupId the group whose trail is being read
     * @param caller  the authenticated caller, already known by the endpoint's
     *                {@code @PreAuthorize} to belong to the group or to hold a platform role
     * @return the whole group's trail for an admin, or the caller's own slice of it for a member
     */
    public Specification<AuditLog> inGroup(Long groupId, AuthPrincipal caller) {
        Specification<AuditLog> scope = AuditLogSpecs.inGroup(groupId);
        if (readsWholeTrail(groupId, caller)) {
            return scope;
        }
        // What a member created is read off the trail itself, not off the entities: only task
        // carries a created_by column, while a CREATE entry names the actor for every type.
        return scope.and(AuditLogSpecs.visibleToMember(groupId, caller.userId()));
    }

    // Any platform role above USER reads across households - the same span the endpoint's
    // hasRole('ADMIN') already admits through the role hierarchy.
    private boolean readsWholeTrail(Long groupId, AuthPrincipal caller) {
        return caller.role() != UserRole.USER || groupSecurity.isAdminOfGroup(groupId);
    }
}
