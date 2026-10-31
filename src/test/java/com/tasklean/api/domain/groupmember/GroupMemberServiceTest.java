package com.tasklean.api.domain.groupmember;

import com.tasklean.api.common.exception.BusinessRuleException;
import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditEntityType;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final String GROUP_UID = "group-uid";
    private static final String MEMBER_UID = "member-uid";
    private static final String USER_UID = "user-uid";

    private Group group() {
        return Group.builder().idGroup(GROUP_ID).uid(GROUP_UID).build();
    }

    private User user() {
        return User.builder().idUser(USER_ID).uid(USER_UID).name("Alice").lastName("Smith").build();
    }

    private GroupMember member(GroupRole role, boolean active) {
        return GroupMember.builder()
                .idGroupMember(MEMBER_ID)
                .uid(MEMBER_UID)
                .role(role)
                .isActive(active)
                .group(group())
                .user(user())
                .build();
    }

    private void stubGroup() {
        when(groupRepository.findByUid(GROUP_UID)).thenReturn(Optional.of(group()));
    }

    private void stubMembership(GroupMember member) {
        when(groupMemberRepository.findByUid(MEMBER_UID)).thenReturn(Optional.of(member));
    }

    private void stubAdminCount(long count) {
        when(groupMemberRepository.countByGroupIdGroupAndRoleAndIsActiveTrue(GROUP_ID, GroupRole.GROUP_ADMIN))
                .thenReturn(count);
    }

    private GroupMemberRequest request(GroupRole role) {
        GroupMemberRequest request = new GroupMemberRequest();
        request.setGroupUid(GROUP_UID);
        request.setUserUid(USER_UID);
        request.setRole(role);
        return request;
    }

    // --- lookups ---

    @Test
    void getMember_unknownUid_throwsNotFound() {
        when(groupMemberRepository.findByUid(MEMBER_UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupMemberService.getMember(MEMBER_UID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getMembers_unknownGroup_throwsNotFound() {
        when(groupRepository.findByUid(GROUP_UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupMemberService.getMembers(GROUP_UID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(groupMemberRepository, never()).findByGroupUidAndIsActiveTrue(any());
    }

    @Test
    void getMembers_activeMembers_carryTheUserSummaryNotIds() {
        stubGroup();
        when(groupMemberRepository.findByGroupUidAndIsActiveTrue(GROUP_UID))
                .thenReturn(List.of(member(GroupRole.GROUP_ADMIN, true)));

        var members = groupMemberService.getMembers(GROUP_UID);

        // A roster has to be renderable from this one response; before, it held only numeric ids.
        assertThat(members).singleElement().satisfies(m -> {
            assertThat(m.getUid()).isEqualTo(MEMBER_UID);
            assertThat(m.getUser().getUid()).isEqualTo(USER_UID);
            assertThat(m.getUser().getName()).isEqualTo("Alice");
            assertThat(m.getUser().getLastName()).isEqualTo("Smith");
        });
    }

    // --- addMember ---

    @Test
    void addMember_newMembership_persistsWithRoleAndUid() {
        stubGroup();
        when(groupMemberRepository.findByUserUidAndGroupUid(USER_UID, GROUP_UID)).thenReturn(Optional.empty());
        when(userRepository.findByUid(USER_UID)).thenReturn(Optional.of(user()));
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(i -> i.getArgument(0));

        var response = groupMemberService.addMember(request(GroupRole.GROUP_MEMBER));

        assertThat(response.getRole()).isEqualTo(GroupRole.GROUP_MEMBER);
        assertThat(response.getUid()).isNotBlank();
    }

    @Test
    void addMember_alreadyMember_throwsDuplicate() {
        stubGroup();
        when(groupMemberRepository.findByUserUidAndGroupUid(USER_UID, GROUP_UID))
                .thenReturn(Optional.of(member(GroupRole.GROUP_MEMBER, true)));

        assertThatThrownBy(() -> groupMemberService.addMember(request(GroupRole.GROUP_MEMBER)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void addMember_unknownGroup_throwsNotFound() {
        when(groupRepository.findByUid(GROUP_UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupMemberService.addMember(request(GroupRole.GROUP_MEMBER)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void addMember_unknownUser_throwsNotFound() {
        stubGroup();
        when(groupMemberRepository.findByUserUidAndGroupUid(USER_UID, GROUP_UID)).thenReturn(Optional.empty());
        when(userRepository.findByUid(USER_UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupMemberService.addMember(request(GroupRole.GROUP_MEMBER)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(groupMemberRepository, never()).save(any());
    }

    // --- updateMemberRole (last-admin protection) ---

    @Test
    void updateMemberRole_demotingLastAdmin_throws() {
        stubMembership(member(GroupRole.GROUP_ADMIN, true));
        stubAdminCount(1L);

        assertThatThrownBy(() -> groupMemberService.updateMemberRole(MEMBER_UID, GroupRole.GROUP_MEMBER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one admin");
        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void updateMemberRole_demotingWithAnotherAdmin_succeeds() {
        GroupMember member = member(GroupRole.GROUP_ADMIN, true);
        stubMembership(member);
        stubAdminCount(2L);
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(i -> i.getArgument(0));

        groupMemberService.updateMemberRole(MEMBER_UID, GroupRole.GROUP_MEMBER);

        assertThat(member.getRole()).isEqualTo(GroupRole.GROUP_MEMBER);
    }

    @Test
    void updateMemberRole_promotingMember_succeeds() {
        GroupMember member = member(GroupRole.GROUP_MEMBER, true);
        stubMembership(member);
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(i -> i.getArgument(0));

        groupMemberService.updateMemberRole(MEMBER_UID, GroupRole.GROUP_ADMIN);

        assertThat(member.getRole()).isEqualTo(GroupRole.GROUP_ADMIN);
    }

    // --- removeMember: the same state change, two different events ---

    @Test
    void removeMember_someoneElse_recordsMemberRemoved() {
        GroupMember member = member(GroupRole.GROUP_MEMBER, true);
        stubMembership(member);

        boolean left = groupMemberService.removeMember(MEMBER_UID, OTHER_USER_ID);

        assertThat(left).isFalse();
        assertThat(member.getIsActive()).isFalse();
        assertThat(member.getDateLeft()).isNotNull();
        verify(auditLogService).recordEvent(eq(AuditEntityType.GROUP_MEMBER), eq(MEMBER_ID),
                eq(AuditAction.MEMBER_REMOVED), any(), any());
    }

    @Test
    void removeMember_ownMembership_recordsMemberLeft() {
        GroupMember member = member(GroupRole.GROUP_MEMBER, true);
        stubMembership(member);

        boolean left = groupMemberService.removeMember(MEMBER_UID, USER_ID);

        // Walking out and being shown the door are the same row change but not the same event.
        assertThat(left).isTrue();
        verify(auditLogService).recordEvent(eq(AuditEntityType.GROUP_MEMBER), eq(MEMBER_ID),
                eq(AuditAction.MEMBER_LEFT), any(), any());
    }

    @Test
    void removeMember_lastAdmin_throws() {
        stubMembership(member(GroupRole.GROUP_ADMIN, true));
        stubAdminCount(1L);

        assertThatThrownBy(() -> groupMemberService.removeMember(MEMBER_UID, USER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one admin");
        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void removeMember_adminWithAnotherAdmin_softDeletes() {
        GroupMember member = member(GroupRole.GROUP_ADMIN, true);
        stubMembership(member);
        stubAdminCount(2L);

        groupMemberService.removeMember(MEMBER_UID, OTHER_USER_ID);

        assertThat(member.getIsActive()).isFalse();
        verify(groupMemberRepository).save(member);
    }

    @Test
    void removeMember_unknownUid_throwsNotFound() {
        when(groupMemberRepository.findByUid(MEMBER_UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupMemberService.removeMember(MEMBER_UID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
