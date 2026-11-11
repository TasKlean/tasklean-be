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
 * can only answer yes or no to the endpoint, never narrow what comes back. The same role decides
 * what the caller may filter by, so that answer rides along rather than asking the group again.
 */
@Component
@RequiredArgsConstructor
class AuditLogVisibility {

    private final GroupSecurity groupSecurity;

    /**
     * What the caller may read of a group's trail, and what they may narrow it by. Both answers
     * come back together because both turn on the same group-role lookup.
     *
     * @param groupId the group whose trail is being read
     * @param caller  the authenticated caller, already known by the endpoint's
     *                {@code @PreAuthorize} to belong to the group or to hold a platform role
     * @return the whole group's trail for an admin, or the caller's own slice of it for a member
     */
    public GroupTrail inGroup(Long groupId, AuthPrincipal caller) {
        Specification<AuditLog> scope = AuditLogSpecs.inGroup(groupId);
        if (readsWholeTrail(groupId, caller)) {
            return new GroupTrail(scope, AuditFilterRights.anyActor());
        }
        // What a member created is read off the trail itself, not off the entities: only task
        // carries a created_by column, while a CREATE entry names the actor for every type.
        return new GroupTrail(scope.and(AuditLogSpecs.visibleToMember(groupId, caller.userId())),
                AuditFilterRights.ownActionsOnly(caller.uid()));
    }

    /** One caller's view of one group's trail: the rows they may read, and what they may ask of them. */
    record GroupTrail(Specification<AuditLog> scope, AuditFilterRights rights) {
    }

    // Any platform role above USER reads across households - the same span the endpoint's
    // hasRole('ADMIN') already admits through the role hierarchy.
    private boolean readsWholeTrail(Long groupId, AuthPrincipal caller) {
        return caller.role() != UserRole.USER || groupSecurity.isAdminOfGroup(groupId);
    }
}
