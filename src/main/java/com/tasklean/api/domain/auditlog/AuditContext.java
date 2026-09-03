package com.tasklean.api.domain.auditlog;

import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.user.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves the ambient request context needed to attribute an audit entry: the acting
 * group member (from the authenticated user and the target group) and the caller's IP.
 * Keeps SecurityContext and request plumbing out of the domain services that record events.
 */
@Component
@RequiredArgsConstructor
public class AuditContext {

    private final GroupMemberRepository groupMemberRepository;

    /**
     * Resolves the group member acting in the current request for the given group —
     * the authenticated user's membership of that group.
     *
     * @param groupId the group the audited action targets
     * @return the acting group member, or {@code null} if there is no authenticated user,
     *         no group id, or the user is not a member of that group
     */
    public GroupMember currentActor(Long groupId) {
        User user = currentUser();
        if (user == null || groupId == null) {
            return null;
        }
        return groupMemberRepository
                .findByUserIdUserAndGroupIdGroup(user.getIdUser(), groupId)
                .orElse(null);
    }

    /**
     * Returns the authenticated user making the current request.
     *
     * @return the current user, or {@code null} if the request is anonymous (e.g. public
     *         auth endpoints, where no caller is set in the security context)
     */
    public User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    /**
     * Returns the IP address of the current request.
     *
     * @return the caller's IP address, or {@code null} if no request is bound to the
     *         current thread (e.g. a scheduled job)
     */
    public String currentIpAddress() {
        HttpServletRequest request = currentRequest();
        return request != null ? request.getRemoteAddr() : null;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }
}
