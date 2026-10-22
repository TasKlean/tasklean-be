package com.tasklean.api.domain.auditlog;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.common.web.RequestContext;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import com.tasklean.api.domain.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditContextTest {

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RequestContext requestContext;

    @InjectMocks
    private AuditContext auditContext;

    @AfterEach
    void clearThreadLocals() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Long userId) {
        AuthPrincipal principal = new AuthPrincipal(userId, "usr-test", "test@example.com", UserRole.USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null));
    }

    // --- currentUserId ---

    @Test
    void currentUserId_authenticated_returnsId() {
        authenticateAs(1L);

        assertThat(auditContext.currentUserId()).isEqualTo(1L);
    }

    @Test
    void currentUserId_noAuthentication_returnsNull() {
        assertThat(auditContext.currentUserId()).isNull();
    }

    @Test
    void currentUserId_nonAuthPrincipal_returnsNull() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousString", null));

        assertThat(auditContext.currentUserId()).isNull();
    }

    // --- currentUser ---

    @Test
    void currentUser_authenticated_returnsUserReference() {
        authenticateAs(1L);
        User reference = User.builder().idUser(1L).build();
        when(userRepository.getReferenceById(1L)).thenReturn(reference);

        assertThat(auditContext.currentUser()).isEqualTo(reference);
    }

    @Test
    void currentUser_noAuthentication_returnsNull() {
        assertThat(auditContext.currentUser()).isNull();
    }

    // --- currentActor ---

    @Test
    void currentActor_memberOfGroup_returnsMembership() {
        authenticateAs(1L);
        GroupMember member = GroupMember.builder().idGroupMember(5L).build();
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(1L, 10L))
                .thenReturn(Optional.of(member));

        assertThat(auditContext.currentActor(10L)).isEqualTo(member);
    }

    @Test
    void currentActor_notAMember_returnsNull() {
        authenticateAs(1L);
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(1L, 10L))
                .thenReturn(Optional.empty());

        assertThat(auditContext.currentActor(10L)).isNull();
    }

    @Test
    void currentActor_noAuthenticatedUser_returnsNull() {
        assertThat(auditContext.currentActor(10L)).isNull();
    }

    @Test
    void currentActor_nullGroupId_returnsNull() {
        authenticateAs(1L);

        assertThat(auditContext.currentActor(null)).isNull();
    }

    // --- currentIpAddress ---

    @Test
    void currentIpAddress_resolvedIp_returnsIt() {
        when(requestContext.clientIp()).thenReturn("203.0.113.7");

        assertThat(auditContext.currentIpAddress()).isEqualTo("203.0.113.7");
    }

    @Test
    void currentIpAddress_noBoundRequest_returnsNull() {
        when(requestContext.clientIp()).thenReturn(null);

        assertThat(auditContext.currentIpAddress()).isNull();
    }
}
