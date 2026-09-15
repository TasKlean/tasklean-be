package com.tasklean.api.domain.group;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.domain.group.dto.GroupResponse;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.groupmember.GroupRole;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRole;
import com.tasklean.api.security.GroupSecurity;
import com.tasklean.api.security.MethodSecuritySliceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the @PreAuthorize wiring on {@link GroupController}: group membership/role checks via
 * {@code @groupSecurity} and the platform-role fallback. GroupSecurity is real; its repositories
 * are mocked to place the caller inside (or outside) the target group.
 */
@WebMvcTest(GroupController.class)
@Import({MethodSecuritySliceConfig.class, GroupSecurity.class})
class GroupControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GroupService groupService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private GroupRepository groupRepository;

    @MockitoBean
    private GroupMemberRepository groupMemberRepository;

    private static final String GROUP_UID = "grp-1";
    private static final Long GROUP_ID = 10L;
    private static final Long USER_ID = 1L;

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

    // --- getGroup: member or platform ADMIN ---

    @Test
    void getGroup_nonMember_forbidden() throws Exception {
        // groupRepository.findByUid unstubbed → Optional.empty() → not a member
        mockMvc.perform(get("/api/groups/{uid}", GROUP_UID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getGroup_member_ok() throws Exception {
        callerIsMemberWithRole(GroupRole.GROUP_MEMBER);
        when(groupService.getGroupByUid(GROUP_UID)).thenReturn(GroupResponse.builder().uid(GROUP_UID).build());

        mockMvc.perform(get("/api/groups/{uid}", GROUP_UID).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void getGroup_platformAdmin_ok() throws Exception {
        when(groupService.getGroupByUid(GROUP_UID)).thenReturn(GroupResponse.builder().uid(GROUP_UID).build());

        mockMvc.perform(get("/api/groups/{uid}", GROUP_UID).with(as(UserRole.ADMIN)))
                .andExpect(status().isOk());
    }

    // --- updateGroup: group admin or platform SUPER_ADMIN ---

    @Test
    void updateGroup_plainMember_forbidden() throws Exception {
        callerIsMemberWithRole(GroupRole.GROUP_MEMBER);

        mockMvc.perform(put("/api/groups/{uid}", GROUP_UID)
                        .with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Home\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateGroup_groupAdmin_ok() throws Exception {
        callerIsMemberWithRole(GroupRole.GROUP_ADMIN);
        when(groupService.updateGroup(org.mockito.ArgumentMatchers.eq(GROUP_UID), org.mockito.ArgumentMatchers.any()))
                .thenReturn(GroupResponse.builder().uid(GROUP_UID).build());

        mockMvc.perform(put("/api/groups/{uid}", GROUP_UID)
                        .with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Home\"}"))
                .andExpect(status().isOk());
    }
}
