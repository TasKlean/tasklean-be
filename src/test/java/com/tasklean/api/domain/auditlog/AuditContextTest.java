package com.tasklean.api.domain.auditlog;

import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditContextTest {

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @InjectMocks
    private AuditContext auditContext;

    @AfterEach
    void clearThreadLocals() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
    }

    private void bindRequestWithIp(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    // --- currentUser ---

    @Test
    void currentUser_authenticatedUserPrincipal_returnsUser() {
        User user = User.builder().idUser(1L).build();
        authenticateAs(user);

        assertThat(auditContext.currentUser()).isEqualTo(user);
    }

    @Test
    void currentUser_noAuthentication_returnsNull() {
        assertThat(auditContext.currentUser()).isNull();
    }

    @Test
    void currentUser_nonUserPrincipal_returnsNull() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousString", null));

        assertThat(auditContext.currentUser()).isNull();
    }

    // --- currentActor ---

    @Test
    void currentActor_memberOfGroup_returnsMembership() {
        User user = User.builder().idUser(1L).build();
        authenticateAs(user);
        GroupMember member = GroupMember.builder().idGroupMember(5L).build();
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(1L, 10L))
                .thenReturn(Optional.of(member));

        assertThat(auditContext.currentActor(10L)).isEqualTo(member);
    }

    @Test
    void currentActor_notAMember_returnsNull() {
        User user = User.builder().idUser(1L).build();
        authenticateAs(user);
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
        authenticateAs(User.builder().idUser(1L).build());

        assertThat(auditContext.currentActor(null)).isNull();
    }

    // --- currentIpAddress ---

    @Test
    void currentIpAddress_boundRequest_returnsRemoteAddr() {
        bindRequestWithIp("203.0.113.7");

        assertThat(auditContext.currentIpAddress()).isEqualTo("203.0.113.7");
    }

    @Test
    void currentIpAddress_noBoundRequest_returnsNull() {
        assertThat(auditContext.currentIpAddress()).isNull();
    }
}
