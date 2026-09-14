package com.tasklean.api.security;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.groupmember.GroupRole;
import com.tasklean.api.domain.user.User;
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
class GroupSecurityTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @InjectMocks
    private GroupSecurity groupSecurity;

    private static final Long USER_ID = 1L;
    private static final Long GROUP_ID = 10L;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        AuthPrincipal principal = new AuthPrincipal(USER_ID, "usr-test", "user@example.com", UserRole.USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null));
    }

    private GroupMember membership(GroupRole role, boolean active) {
        return GroupMember.builder()
                .idGroupMember(5L)
                .role(role)
                .isActive(active)
                .group(Group.builder().idGroup(GROUP_ID).build())
                .user(User.builder().idUser(USER_ID).build())
                .build();
    }

    // --- isAdminOfGroup / isMemberOfGroup ---

    @Test
    void isAdminOfGroup_activeAdmin_returnsTrue() {
        authenticate();
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID))
                .thenReturn(Optional.of(membership(GroupRole.GROUP_ADMIN, true)));

        assertThat(groupSecurity.isAdminOfGroup(GROUP_ID)).isTrue();
    }

    @Test
    void isAdminOfGroup_plainMember_returnsFalse() {
        authenticate();
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID))
                .thenReturn(Optional.of(membership(GroupRole.GROUP_MEMBER, true)));

        assertThat(groupSecurity.isAdminOfGroup(GROUP_ID)).isFalse();
    }

    @Test
    void isAdminOfGroup_inactiveMembership_returnsFalse() {
        authenticate();
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID))
                .thenReturn(Optional.of(membership(GroupRole.GROUP_ADMIN, false)));

        assertThat(groupSecurity.isAdminOfGroup(GROUP_ID)).isFalse();
    }

    @Test
    void isMemberOfGroup_activeMember_returnsTrue() {
        authenticate();
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID))
                .thenReturn(Optional.of(membership(GroupRole.GROUP_MEMBER, true)));

        assertThat(groupSecurity.isMemberOfGroup(GROUP_ID)).isTrue();
    }

    @Test
    void isMemberOfGroup_notAMember_returnsFalse() {
        authenticate();
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID))
                .thenReturn(Optional.empty());

        assertThat(groupSecurity.isMemberOfGroup(GROUP_ID)).isFalse();
    }

    @Test
    void isMemberOfGroup_noAuthentication_returnsFalse() {
        assertThat(groupSecurity.isMemberOfGroup(GROUP_ID)).isFalse();
    }

    // --- isAdmin / isMember by group uid ---

    @Test
    void isAdmin_byUid_activeAdmin_returnsTrue() {
        authenticate();
        when(groupRepository.findByUid("grp-uid"))
                .thenReturn(Optional.of(Group.builder().idGroup(GROUP_ID).build()));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID))
                .thenReturn(Optional.of(membership(GroupRole.GROUP_ADMIN, true)));

        assertThat(groupSecurity.isAdmin("grp-uid")).isTrue();
    }

    @Test
    void isMember_byUid_groupNotFound_returnsFalse() {
        authenticate();
        when(groupRepository.findByUid("missing")).thenReturn(Optional.empty());

        assertThat(groupSecurity.isMember("missing")).isFalse();
    }

    // --- membership-id based ---

    @Test
    void canManageMember_callerAdminOfMembersGroup_returnsTrue() {
        authenticate();
        when(groupMemberRepository.findById(5L)).thenReturn(Optional.of(membership(GroupRole.GROUP_MEMBER, true)));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID))
                .thenReturn(Optional.of(membership(GroupRole.GROUP_ADMIN, true)));

        assertThat(groupSecurity.canManageMember(5L)).isTrue();
    }

    @Test
    void canViewMember_callerMemberOfMembersGroup_returnsTrue() {
        authenticate();
        when(groupMemberRepository.findById(5L)).thenReturn(Optional.of(membership(GroupRole.GROUP_MEMBER, true)));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID))
                .thenReturn(Optional.of(membership(GroupRole.GROUP_MEMBER, true)));

        assertThat(groupSecurity.canViewMember(5L)).isTrue();
    }

    @Test
    void isSelfMember_ownMembership_returnsTrue() {
        authenticate();
        when(groupMemberRepository.findById(5L)).thenReturn(Optional.of(membership(GroupRole.GROUP_MEMBER, true)));

        assertThat(groupSecurity.isSelfMember(5L)).isTrue();
    }

    @Test
    void isSelfMember_othersMembership_returnsFalse() {
        authenticate();
        GroupMember others = GroupMember.builder()
                .idGroupMember(5L)
                .group(Group.builder().idGroup(GROUP_ID).build())
                .user(User.builder().idUser(999L).build())
                .build();
        when(groupMemberRepository.findById(5L)).thenReturn(Optional.of(others));

        assertThat(groupSecurity.isSelfMember(5L)).isFalse();
    }
}
