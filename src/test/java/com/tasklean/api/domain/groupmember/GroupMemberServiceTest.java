package com.tasklean.api.domain.groupmember;

import com.tasklean.api.common.exception.BusinessRuleException;
import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditChanges;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditFieldChange;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.dto.GroupMemberRequest;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyString;
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
    private static final Long ACTOR_ID = 6L;
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
        when(groupRepository.findByUidAndIsActiveTrue(GROUP_UID)).thenReturn(Optional.of(group()));
    }

    // A second membership, so "who did this" can be told apart from "who it happened to".
    private GroupMember actor() {
        return GroupMember.builder()
                .idGroupMember(ACTOR_ID)
                .uid("actor-member-uid")
                .role(GroupRole.GROUP_ADMIN)
                .isActive(true)
                .group(group())
                .user(User.builder().idUser(OTHER_USER_ID).uid("actor-user-uid").build())
                .build();
    }

    private void stubActor(GroupMember actor) {
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(OTHER_USER_ID, GROUP_ID))
                .thenReturn(Optional.ofNullable(actor));
    }

    private void stubMembership(GroupMember member) {
        when(groupMemberRepository.findByUid(MEMBER_UID)).thenReturn(Optional.of(member));
    }

    // The read path has its own finder, the one that fetches the users the response renders.
    private void stubDetailedMembership(GroupMember member) {
        when(groupMemberRepository.findDetailedByUid(MEMBER_UID)).thenReturn(Optional.of(member));
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
        when(groupMemberRepository.findDetailedByUid(MEMBER_UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupMemberService.getMember(MEMBER_UID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getMember_anyMembership_usesTheFinderThatFetchesTheUsers() {
        // The response renders three users. Taking them from the plain finder left them as proxies,
        // which only resolved while open-in-view held a session open past the service call.
        stubDetailedMembership(member(GroupRole.GROUP_MEMBER, true));

        groupMemberService.getMember(MEMBER_UID);

        verify(groupMemberRepository, never()).findByUid(anyString());
    }

    @Test
    void deactivateAllForGroup_endsEveryActiveMembership() {
        GroupMember admin = member(GroupRole.GROUP_ADMIN, true);
        GroupMember plain = member(GroupRole.GROUP_MEMBER, true);
        when(groupMemberRepository.findByGroupIdGroupAndIsActiveTrue(GROUP_ID))
                .thenReturn(List.of(admin, plain));

        assertThat(groupMemberService.deactivateAllForGroup(group(), USER_ID)).isEqualTo(2);

        assertThat(admin.getIsActive()).isFalse();
        assertThat(plain.getIsActive()).isFalse();
        assertThat(admin.getDateLeft()).isNotNull();
    }

    @Test
    void deactivateAllForGroup_lastAdmin_isNotProtected() {
        // The last-admin guard exists to keep a living group governable. Applying it here would
        // make a group with one admin undeletable.
        GroupMember onlyAdmin = member(GroupRole.GROUP_ADMIN, true);
        when(groupMemberRepository.findByGroupIdGroupAndIsActiveTrue(GROUP_ID))
                .thenReturn(List.of(onlyAdmin));

        assertThat(groupMemberService.deactivateAllForGroup(group(), USER_ID)).isEqualTo(1);
        assertThat(onlyAdmin.getIsActive()).isFalse();
    }

    @Test
    void deactivateAllForGroup_auditsOneEventPerMember() {
        when(groupMemberRepository.findByGroupIdGroupAndIsActiveTrue(GROUP_ID))
                .thenReturn(List.of(member(GroupRole.GROUP_ADMIN, true), member(GroupRole.GROUP_MEMBER, true)));

        groupMemberService.deactivateAllForGroup(group(), USER_ID);

        verify(auditLogService, times(2)).recordEvent(
                eq(AuditEntityType.GROUP_MEMBER), any(), eq(AuditAction.MEMBER_REMOVED), any(), any());
    }

    @Test
    void deactivateAllForGroup_noMembers_doesNothing() {
        when(groupMemberRepository.findByGroupIdGroupAndIsActiveTrue(GROUP_ID)).thenReturn(List.of());

        assertThat(groupMemberService.deactivateAllForGroup(group(), USER_ID)).isZero();
        verifyNoInteractions(auditLogService);
    }

    @Test
    void addMember_callerIsAMemberOfTheGroup_attributesTheAdd() {
        stubGroup();
        GroupMember acting = actor();
        stubActor(acting);
        when(groupMemberRepository.findByUserUidAndGroupUid(USER_UID, GROUP_UID)).thenReturn(Optional.empty());
        when(userRepository.findByUid(USER_UID)).thenReturn(Optional.of(user()));
        ArgumentCaptor<GroupMember> saved = ArgumentCaptor.forClass(GroupMember.class);
        when(groupMemberRepository.save(saved.capture())).thenAnswer(i -> i.getArgument(0));

        groupMemberService.addMember(request(GroupRole.GROUP_MEMBER), OTHER_USER_ID);

        assertThat(saved.getValue().getAddedBy()).isEqualTo(acting);
    }

    @Test
    void addMember_callerHasNoMembershipOfTheGroup_leavesAddedByNull() {
        // The columns point at a membership, not a user, so a SUPER_ADMIN acting from the platform
        // dashboard has nothing to point at. Null is the designed answer, not a missed case.
        stubGroup();
        stubActor(null);
        when(groupMemberRepository.findByUserUidAndGroupUid(USER_UID, GROUP_UID)).thenReturn(Optional.empty());
        when(userRepository.findByUid(USER_UID)).thenReturn(Optional.of(user()));
        ArgumentCaptor<GroupMember> saved = ArgumentCaptor.forClass(GroupMember.class);
        when(groupMemberRepository.save(saved.capture())).thenAnswer(i -> i.getArgument(0));

        groupMemberService.addMember(request(GroupRole.GROUP_MEMBER), OTHER_USER_ID);

        assertThat(saved.getValue().getAddedBy()).isNull();
    }

    @Test
    void removeMember_someoneElse_attributesTheRemover() {
        GroupMember member = member(GroupRole.GROUP_MEMBER, true);
        GroupMember acting = actor();
        stubMembership(member);
        stubActor(acting);

        groupMemberService.removeMember(MEMBER_UID, OTHER_USER_ID);

        assertThat(member.getRemovedBy()).isEqualTo(acting);
    }

    @Test
    void removeMember_ownMembership_attributesThemselves() {
        GroupMember member = member(GroupRole.GROUP_MEMBER, true);
        stubMembership(member);

        groupMemberService.removeMember(MEMBER_UID, USER_ID);

        // Leaving is self-attributed without a second lookup: the membership is the actor.
        assertThat(member.getRemovedBy()).isEqualTo(member);
    }

    @Test
    void deactivateAllForGroup_attributesTheMemberWhoDeletedTheGroup() {
        GroupMember target = member(GroupRole.GROUP_MEMBER, true);
        GroupMember acting = actor();
        when(groupMemberRepository.findByGroupIdGroupAndIsActiveTrue(GROUP_ID))
                .thenReturn(List.of(target));
        stubActor(acting);

        groupMemberService.deactivateAllForGroup(group(), OTHER_USER_ID);

        assertThat(target.getRemovedBy()).isEqualTo(acting);
    }

    @Test
    void updateMemberRole_inactiveMembership_throwsNotFound() {
        // Left or removed is history. Giving it a role would have set GROUP_ADMIN on a row nobody
        // holds, and a rejoin resets the role anyway, so the write was pure noise.
        stubMembership(member(GroupRole.GROUP_MEMBER, false));

        assertThatThrownBy(() -> groupMemberService.updateMemberRole(MEMBER_UID, GroupRole.GROUP_ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void removeMember_alreadyRemoved_throwsNotFound() {
        // Without this it stamped a second date_left over the first and audited a second removal.
        stubMembership(member(GroupRole.GROUP_MEMBER, false));

        assertThatThrownBy(() -> groupMemberService.removeMember(MEMBER_UID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(groupMemberRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void getMember_inactiveMembership_isStillReadable() {
        // Deliberately asymmetric with the writes above: the row is how the API answers "who
        // removed whom", so reading it has to keep working after the member is gone.
        GroupMember gone = member(GroupRole.GROUP_MEMBER, false);
        gone.setRemovedBy(actor());
        stubDetailedMembership(gone);

        assertThat(groupMemberService.getMember(MEMBER_UID).getRemovedBy()).isNotNull();
    }

    @Test
    void getMembers_unknownGroup_throwsNotFound() {
        when(groupRepository.findByUidAndIsActiveTrue(GROUP_UID)).thenReturn(Optional.empty());

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

        var response = groupMemberService.addMember(request(GroupRole.GROUP_MEMBER), USER_ID);

        assertThat(response.getRole()).isEqualTo(GroupRole.GROUP_MEMBER);
        assertThat(response.getUid()).isNotBlank();
    }

    @Test
    void addMember_alreadyMember_throwsDuplicate() {
        stubGroup();
        when(groupMemberRepository.findByUserUidAndGroupUid(USER_UID, GROUP_UID))
                .thenReturn(Optional.of(member(GroupRole.GROUP_MEMBER, true)));

        assertThatThrownBy(() -> groupMemberService.addMember(request(GroupRole.GROUP_MEMBER), USER_ID))
                .isInstanceOf(DuplicateResourceException.class);
        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void addMember_unknownGroup_throwsNotFound() {
        when(groupRepository.findByUidAndIsActiveTrue(GROUP_UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupMemberService.addMember(request(GroupRole.GROUP_MEMBER), USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void addMember_unknownUser_throwsNotFound() {
        stubGroup();
        when(groupMemberRepository.findByUserUidAndGroupUid(USER_UID, GROUP_UID)).thenReturn(Optional.empty());
        when(userRepository.findByUid(USER_UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupMemberService.addMember(request(GroupRole.GROUP_MEMBER), USER_ID))
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
        ArgumentCaptor<AuditChanges> changes = ArgumentCaptor.forClass(AuditChanges.class);
        verify(auditLogService).recordEvent(eq(AuditEntityType.GROUP_MEMBER), any(),
                eq(AuditAction.ROLE_CHANGED), any(), any(), changes.capture());
        // The role's values are kept: this is what lets the feed say "to Admin" rather than
        // only "the role changed".
        assertThat(changes.getValue().entries())
                .containsExactly(new AuditFieldChange("role", "GROUP_MEMBER", "GROUP_ADMIN"));
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
