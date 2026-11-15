package com.tasklean.api.domain.groupmember;

import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditChanges;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditFieldChange;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupService;
import com.tasklean.api.domain.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MembershipClosureTest {

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private GroupService groupService;

    @Mock
    private AuditLogService auditLogService;

    @Spy
    private Clock clock = Clock.systemUTC();

    @InjectMocks
    private MembershipClosure membershipClosure;

    private static final Long SUBJECT_ID = 1L;
    private static final Long ADMIN_ID = 99L;
    private static final LocalDateTime JOINED = LocalDateTime.now(ZoneOffset.UTC).minusDays(30);

    @Test
    void closeFor_noMemberships_doesNothing() {
        when(groupMemberRepository.findByUserIdUserAndIsActiveTrue(SUBJECT_ID)).thenReturn(List.of());

        membershipClosure.closeFor(subject(), SUBJECT_ID);

        verify(groupMemberRepository, never()).save(any());
        verifyNoInteractions(auditLogService, groupService);
    }

    @Test
    void closeFor_selfService_endsTheMembershipAsHavingLeft() {
        Group group = group(1L, "grp-1");
        GroupMember membership = member(10L, group, GroupRole.GROUP_MEMBER, JOINED);
        onlyMembership(membership);
        survivors(group, member(11L, group, GroupRole.GROUP_ADMIN, JOINED));

        membershipClosure.closeFor(subject(), SUBJECT_ID);

        assertThat(membership.getIsActive()).isFalse();
        assertThat(membership.getDateLeft()).isNotNull();
        // Nobody removed them, so the row points at itself - the same attribution leaving does.
        assertThat(membership.getRemovedBy()).isSameAs(membership);
        verify(auditLogService).recordEvent(eq(AuditEntityType.GROUP_MEMBER), eq(10L),
                eq(AuditAction.MEMBER_LEFT), anyString(), eq(group));
    }

    @Test
    void closeFor_adminDeletingSomeoneElse_recordsThemAsRemoved() {
        Group group = group(1L, "grp-1");
        GroupMember membership = member(10L, group, GroupRole.GROUP_MEMBER, JOINED);
        GroupMember actingAdmin = member(12L, group, GroupRole.GROUP_ADMIN, JOINED);
        onlyMembership(membership);
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(ADMIN_ID, 1L))
                .thenReturn(Optional.of(actingAdmin));
        survivors(group, actingAdmin);

        membershipClosure.closeFor(subject(), ADMIN_ID);

        // An admin who is also in the household is attributed with ending the membership.
        assertThat(membership.getRemovedBy()).isSameAs(actingAdmin);
        verify(auditLogService).recordEvent(eq(AuditEntityType.GROUP_MEMBER), eq(10L),
                eq(AuditAction.MEMBER_REMOVED), anyString(), eq(group));
    }

    @Test
    void closeFor_adminFromOutsideTheHousehold_leavesTheAttributionUnset() {
        Group group = group(1L, "grp-1");
        GroupMember membership = member(10L, group, GroupRole.GROUP_MEMBER, JOINED);
        onlyMembership(membership);
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(ADMIN_ID, 1L))
                .thenReturn(Optional.empty());
        survivors(group, member(11L, group, GroupRole.GROUP_ADMIN, JOINED));

        membershipClosure.closeFor(subject(), ADMIN_ID);

        // The column points at a membership, so a platform admin who is not in the group has
        // nothing to point at. Null is the honest answer, not a fallback to the subject.
        assertThat(membership.getRemovedBy()).isNull();
    }

    @Test
    void closeFor_householdKeepsAnAdmin_promotesNobody() {
        Group group = group(1L, "grp-1");
        GroupMember membership = member(10L, group, GroupRole.GROUP_MEMBER, JOINED);
        GroupMember remainingAdmin = member(11L, group, GroupRole.GROUP_ADMIN, JOINED);
        onlyMembership(membership);
        survivors(group, remainingAdmin);

        membershipClosure.closeFor(subject(), SUBJECT_ID);

        assertThat(remainingAdmin.getRole()).isEqualTo(GroupRole.GROUP_ADMIN);
        verify(auditLogService, never()).recordEvent(anyString(), anyLong(),
                eq(AuditAction.ROLE_CHANGED), anyString(), any(), any());
        verifyNoInteractions(groupService);
    }

    @Test
    void closeFor_lastAdminLeaving_promotesTheLongestStandingSurvivor() {
        Group group = group(1L, "grp-1");
        GroupMember departingAdmin = member(10L, group, GroupRole.GROUP_ADMIN, JOINED);
        GroupMember older = member(11L, group, GroupRole.GROUP_MEMBER, JOINED.plusDays(1));
        GroupMember newer = member(12L, group, GroupRole.GROUP_MEMBER, JOINED.plusDays(2));
        onlyMembership(departingAdmin);
        // The query orders by join date, so the first row is the heir.
        survivors(group, older, newer);

        membershipClosure.closeFor(subject(), SUBJECT_ID);

        // The invariant: an active household always has an active admin.
        assertThat(older.getRole()).isEqualTo(GroupRole.GROUP_ADMIN);
        assertThat(newer.getRole()).isEqualTo(GroupRole.GROUP_MEMBER);
        verify(groupMemberRepository).save(older);
        verifyNoInteractions(groupService);
    }

    @Test
    void closeFor_promotion_auditsTheRoleItMoved() {
        Group group = group(1L, "grp-1");
        GroupMember departingAdmin = member(10L, group, GroupRole.GROUP_ADMIN, JOINED);
        GroupMember heir = member(11L, group, GroupRole.GROUP_MEMBER, JOINED.plusDays(1));
        onlyMembership(departingAdmin);
        survivors(group, heir);

        membershipClosure.closeFor(subject(), SUBJECT_ID);

        ArgumentCaptor<AuditChanges> changes = ArgumentCaptor.forClass(AuditChanges.class);
        verify(auditLogService).recordEvent(eq(AuditEntityType.GROUP_MEMBER), eq(11L),
                eq(AuditAction.ROLE_CHANGED), anyString(), eq(group), changes.capture());
        // A role is shown, not withheld: it is already visible on the roster and meaningless
        // out of context, exactly as an ordinary role change records it.
        assertThat(changes.getValue().entries())
                .containsExactly(new AuditFieldChange("role", "GROUP_MEMBER", "GROUP_ADMIN"));
    }

    @Test
    void closeFor_lastMemberLeaving_deletesTheEmptyHousehold() {
        Group group = group(1L, "grp-1");
        GroupMember soleMember = member(10L, group, GroupRole.GROUP_ADMIN, JOINED);
        onlyMembership(soleMember);
        survivors(group);

        membershipClosure.closeFor(subject(), SUBJECT_ID);

        // An empty household still holds a live invite code, so it cannot just be left behind.
        verify(groupService).deleteGroup("grp-1", SUBJECT_ID);
    }

    @Test
    void closeFor_householdAlreadyDeleted_isNotRepaired() {
        Group deleted = group(1L, "grp-1");
        deleted.setIsActive(false);
        GroupMember membership = member(10L, deleted, GroupRole.GROUP_ADMIN, JOINED);
        onlyMembership(membership);

        membershipClosure.closeFor(subject(), SUBJECT_ID);

        // A membership can outlive its group's soft-deletion; there is nothing left to administer.
        assertThat(membership.getIsActive()).isFalse();
        verify(groupMemberRepository, never()).findSurvivingMemberships(anyLong());
        verifyNoInteractions(groupService);
    }

    @Test
    void closeFor_severalHouseholds_repairsEveryOneOfThem() {
        Group first = group(1L, "grp-1");
        Group second = group(2L, "grp-2");
        GroupMember inFirst = member(10L, first, GroupRole.GROUP_ADMIN, JOINED);
        GroupMember inSecond = member(20L, second, GroupRole.GROUP_ADMIN, JOINED);
        GroupMember firstHeir = member(11L, first, GroupRole.GROUP_MEMBER, JOINED.plusDays(1));
        when(groupMemberRepository.findByUserIdUserAndIsActiveTrue(SUBJECT_ID))
                .thenReturn(List.of(inFirst, inSecond));
        survivors(first, firstHeir);
        survivors(second);

        membershipClosure.closeFor(subject(), SUBJECT_ID);

        assertThat(firstHeir.getRole()).isEqualTo(GroupRole.GROUP_ADMIN);
        verify(groupService).deleteGroup("grp-2", SUBJECT_ID);
    }

    @Test
    void closeFor_endsEveryMembershipBeforeLookingForAnHeir() {
        Group first = group(1L, "grp-1");
        Group second = group(2L, "grp-2");
        GroupMember inFirst = member(10L, first, GroupRole.GROUP_ADMIN, JOINED);
        GroupMember inSecond = member(20L, second, GroupRole.GROUP_ADMIN, JOINED);
        when(groupMemberRepository.findByUserIdUserAndIsActiveTrue(SUBJECT_ID))
                .thenReturn(List.of(inFirst, inSecond));
        survivors(first);
        survivors(second);

        membershipClosure.closeFor(subject(), SUBJECT_ID);

        // Succession asks the database who is left, so a check run inside the ending loop would
        // still count a membership this closure has not written yet.
        InOrder order = inOrder(groupMemberRepository);
        order.verify(groupMemberRepository).save(inFirst);
        order.verify(groupMemberRepository).save(inSecond);
        order.verify(groupMemberRepository).findSurvivingMemberships(1L);
    }

    private User subject() {
        return User.builder().idUser(SUBJECT_ID).uid("usr-1").isActive(false).build();
    }

    private void onlyMembership(GroupMember membership) {
        when(groupMemberRepository.findByUserIdUserAndIsActiveTrue(SUBJECT_ID))
                .thenReturn(List.of(membership));
    }

    private void survivors(Group group, GroupMember... remaining) {
        when(groupMemberRepository.findSurvivingMemberships(group.getIdGroup()))
                .thenReturn(List.of(remaining));
    }

    private static Group group(Long id, String uid) {
        return Group.builder().idGroup(id).uid(uid).name("House " + uid).isActive(true).build();
    }

    private static GroupMember member(Long id, Group group, GroupRole role, LocalDateTime joined) {
        return GroupMember.builder()
                .idGroupMember(id)
                .uid("gm-" + id)
                .group(group)
                .role(role)
                .isActive(true)
                .dateJoined(joined)
                .build();
    }
}
