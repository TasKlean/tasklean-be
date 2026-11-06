package com.tasklean.api.domain.auditlog;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.groupmember.GroupRole;
import com.tasklean.api.domain.auditlog.dto.AuditLogPage;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRole;
import com.tasklean.api.security.GroupSecurity;
import com.tasklean.api.security.MethodSecuritySliceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the @PreAuthorize wiring on {@link AuditLogController}. Membership of the group is the
 * gate on the group scope - a plain member is let in and narrowed per row rather than refused -
 * while the account scope is self-scoped and names nobody. GroupSecurity is real; its repositories
 * are mocked to place the caller inside the target group.
 */
@WebMvcTest(AuditLogController.class)
@Import({MethodSecuritySliceConfig.class, GroupSecurity.class})
class AuditLogControllerSecurityTest {

    private static final String GROUP_UID = "grp-1";
    private static final Long GROUP_ID = 10L;
    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogService auditLogService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private GroupRepository groupRepository;

    @MockitoBean
    private GroupMemberRepository groupMemberRepository;

    private AuditLogPage emptyPage() {
        return AuditLogPage.builder().items(List.of()).total(0).build();
    }

    private RequestPostProcessor as(UserRole platformRole) {
        AuthPrincipal principal = new AuthPrincipal(USER_ID, "usr-1", "user@example.com", platformRole);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + platformRole.name()))));
    }

    private void callerIsMemberWithRole(GroupRole role) {
        when(groupRepository.findByUid(GROUP_UID)).thenReturn(Optional.of(Group.builder().idGroup(GROUP_ID).build()));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID))
                .thenReturn(Optional.of(GroupMember.builder()
                        .role(role).isActive(true)
                        .group(Group.builder().idGroup(GROUP_ID).build())
                        .user(User.builder().idUser(USER_ID).build())
                        .build()));
    }

    // --- group scope: any member of that group, or a platform ADMIN reading across households ---

    @Test
    void getGroupLogs_unauthenticated_denied() throws Exception {
        mockMvc.perform(get("/api/audit-logs/groups/" + GROUP_UID))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getGroupLogs_callerNotInTheGroup_denied() throws Exception {
        // The bug this closed: any signed-in user could read any household's trail.
        when(groupRepository.findByUid(GROUP_UID)).thenReturn(Optional.of(Group.builder().idGroup(GROUP_ID).build()));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/audit-logs/groups/" + GROUP_UID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getGroupLogs_unknownGroupUid_denied() throws Exception {
        when(groupRepository.findByUid(GROUP_UID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/audit-logs/groups/" + GROUP_UID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getGroupLogs_plainMemberOfTheGroup_ok() throws Exception {
        // Let in and narrowed inside the query, which is the point of this scope.
        callerIsMemberWithRole(GroupRole.GROUP_MEMBER);
        when(auditLogService.getGroupLogs(eq(GROUP_UID), any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/audit-logs/groups/" + GROUP_UID).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void getGroupLogs_groupAdmin_ok() throws Exception {
        callerIsMemberWithRole(GroupRole.GROUP_ADMIN);
        when(auditLogService.getGroupLogs(eq(GROUP_UID), any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/audit-logs/groups/" + GROUP_UID).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void getGroupLogs_memberWhoLeftTheGroup_denied() throws Exception {
        when(groupRepository.findByUid(GROUP_UID)).thenReturn(Optional.of(Group.builder().idGroup(GROUP_ID).build()));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID))
                .thenReturn(Optional.of(GroupMember.builder().role(GroupRole.GROUP_ADMIN).isActive(false).build()));

        mockMvc.perform(get("/api/audit-logs/groups/" + GROUP_UID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getGroupLogs_platformAdminNotInTheGroup_ok() throws Exception {
        when(groupRepository.findByUid(GROUP_UID)).thenReturn(Optional.of(Group.builder().idGroup(GROUP_ID).build()));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID)).thenReturn(Optional.empty());
        when(auditLogService.getGroupLogs(eq(GROUP_UID), any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/audit-logs/groups/" + GROUP_UID).with(as(UserRole.ADMIN)))
                .andExpect(status().isOk());
    }

    // --- account scope: self, so being signed in is the whole check ---

    @Test
    void getAccountLogs_unauthenticated_denied() throws Exception {
        mockMvc.perform(get("/api/audit-logs/account"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getAccountLogs_anySignedInUser_ok() throws Exception {
        // Nothing to authorize: the subject is read off the token, not out of the request.
        when(auditLogService.getAccountLogs(eq(USER_ID), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/audit-logs/account").with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }
}
