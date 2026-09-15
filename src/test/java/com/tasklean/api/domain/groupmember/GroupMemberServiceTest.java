package com.tasklean.api.domain.groupmember;

import com.tasklean.api.common.exception.BusinessRuleException;
import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.dto.GroupMemberRequest;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupMemberServiceTest {

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private AuditLogService auditLogService;

    @Spy
    private Clock clock = Clock.systemUTC();

    @InjectMocks
    private GroupMemberService groupMemberService;

    private static final Long MEMBER_ID = 5L;
    private static final Long GROUP_ID = 10L;

    private GroupMember admin(boolean active) {
        return GroupMember.builder()
                .idGroupMember(MEMBER_ID)
                .role(GroupRole.GROUP_ADMIN)
                .isActive(active)
                .group(Group.builder().idGroup(GROUP_ID).build())
                .user(User.builder().idUser(1L).build())
                .build();
    }

    // --- addMember ---

    @Test
    void addMember_newMembership_persistsWithRole() {
        GroupMemberRequest request = new GroupMemberRequest();
        request.setUserId(1L);
        request.setGroupId(GROUP_ID);
        request.setRole(GroupRole.GROUP_MEMBER);

        when(groupMemberRepository.existsByUserIdUserAndGroupIdGroup(1L, GROUP_ID)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().idUser(1L).build()));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(Group.builder().idGroup(GROUP_ID).build()));
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(i -> i.getArgument(0));

        var response = groupMemberService.addMember(request);

        assertThat(response.getRole()).isEqualTo(GroupRole.GROUP_MEMBER);
    }

    @Test
    void addMember_alreadyMember_throwsDuplicate() {
        GroupMemberRequest request = new GroupMemberRequest();
        request.setUserId(1L);
        request.setGroupId(GROUP_ID);
        request.setRole(GroupRole.GROUP_MEMBER);

        when(groupMemberRepository.existsByUserIdUserAndGroupIdGroup(1L, GROUP_ID)).thenReturn(true);

        assertThatThrownBy(() -> groupMemberService.addMember(request))
                .isInstanceOf(DuplicateResourceException.class);
        verify(groupMemberRepository, never()).save(any());
    }

    // --- updateMemberRole (last-admin protection) ---

    @Test
    void updateMemberRole_demotingLastAdmin_throws() {
        when(groupMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(admin(true)));
        when(groupMemberRepository.countByGroupIdGroupAndRoleAndIsActiveTrue(GROUP_ID, GroupRole.GROUP_ADMIN))
                .thenReturn(1L);

        assertThatThrownBy(() -> groupMemberService.updateMemberRole(MEMBER_ID, GroupRole.GROUP_MEMBER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one admin");
        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void updateMemberRole_demotingWithAnotherAdmin_succeeds() {
        GroupMember member = admin(true);
        when(groupMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(groupMemberRepository.countByGroupIdGroupAndRoleAndIsActiveTrue(GROUP_ID, GroupRole.GROUP_ADMIN))
                .thenReturn(2L);
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(i -> i.getArgument(0));

        groupMemberService.updateMemberRole(MEMBER_ID, GroupRole.GROUP_MEMBER);

        assertThat(member.getRole()).isEqualTo(GroupRole.GROUP_MEMBER);
    }

    @Test
    void updateMemberRole_promotingMember_succeeds() {
        GroupMember member = GroupMember.builder()
                .idGroupMember(MEMBER_ID)
                .role(GroupRole.GROUP_MEMBER)
                .isActive(true)
                .group(Group.builder().idGroup(GROUP_ID).build())
                .user(User.builder().idUser(1L).build())
                .build();
        when(groupMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(i -> i.getArgument(0));

        groupMemberService.updateMemberRole(MEMBER_ID, GroupRole.GROUP_ADMIN);

        assertThat(member.getRole()).isEqualTo(GroupRole.GROUP_ADMIN);
    }

    // --- removeMember (last-admin protection) ---

    @Test
    void removeMember_lastAdmin_throws() {
        when(groupMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(admin(true)));
        when(groupMemberRepository.countByGroupIdGroupAndRoleAndIsActiveTrue(GROUP_ID, GroupRole.GROUP_ADMIN))
                .thenReturn(1L);

        assertThatThrownBy(() -> groupMemberService.removeMember(MEMBER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one admin");
        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void removeMember_withAnotherAdmin_softDeletes() {
        GroupMember member = admin(true);
        when(groupMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(groupMemberRepository.countByGroupIdGroupAndRoleAndIsActiveTrue(GROUP_ID, GroupRole.GROUP_ADMIN))
                .thenReturn(2L);

        groupMemberService.removeMember(MEMBER_ID);

        assertThat(member.getIsActive()).isFalse();
        assertThat(member.getDateLeft()).isNotNull();
        verify(groupMemberRepository).save(member);
    }
}
