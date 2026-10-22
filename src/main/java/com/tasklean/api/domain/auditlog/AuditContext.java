package com.tasklean.api.domain.auditlog;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.common.web.RequestContext;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the ambient request context needed to attribute an audit entry: the acting
 * group member (from the authenticated user and the target group) and the caller's IP.
 * Keeps SecurityContext and request plumbing out of the domain services that record events.
 */
@Component
@RequiredArgsConstructor
public class AuditContext {

    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final RequestContext requestContext;

    /**
     * Resolves the group member acting in the current request for the given group —
     * the authenticated user's membership of that group.
     *
     * @param groupId the group the audited action targets
     * @return the acting group member, or {@code null} if there is no authenticated user,
     *         no group id, or the user is not a member of that group
     */
    public GroupMember currentActor(Long groupId) {
        Long userId = currentUserId();
        if (userId == null || groupId == null) {
            return null;
        }
        return groupMemberRepository
                .findByUserIdUserAndGroupIdGroup(userId, groupId)
                .orElse(null);
    }

    /**
     * Returns a reference to the authenticated user making the current request, for use as an
     * audit-log foreign key. Returns a lazy proxy (no database load) — only its id is read when
     * the audit entry is persisted.
     *
     * @return a reference to the current user, or {@code null} if the request is anonymous
     *         (e.g. public auth endpoints, where no caller is set in the security context)
     */
    public User currentUser() {
        Long userId = currentUserId();
        return userId != null ? userRepository.getReferenceById(userId) : null;
    }

    /**
     * Returns a reference to the given user for use as an audit-log foreign key, without a database
     * load. Used to attribute self-service account events (register, login, …) to their subject on
     * anonymous endpoints, where {@link #currentUser()} would be {@code null}.
     *
     * @param userId the subject user's id
     * @return a lazy reference to that user
     */
    public User userReference(Long userId) {
        return userRepository.getReferenceById(userId);
    }

    /**
     * Returns the id of the authenticated user making the current request.
     *
     * @return the current user's id, or {@code null} if the request is anonymous
     */
    public Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            return principal.userId();
        }
        return null;
    }

    /**
     * Returns the end user's IP for the current request. Delegates so the audit trail and the
     * sign-in path record the same address.
     *
     * @return the caller's IP address, or {@code null} if no request is bound to the
     *         current thread (e.g. a scheduled job)
     */
    public String currentIpAddress() {
        return requestContext.clientIp();
    }
}
