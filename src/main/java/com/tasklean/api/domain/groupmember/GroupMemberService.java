package com.tasklean.api.domain.groupmember;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.BusinessRuleException;
import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditChanges;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.dto.GroupMemberRequest;
import com.tasklean.api.domain.groupmember.dto.GroupMemberResponse;
import com.tasklean.api.domain.task.TaskAssignmentRelease;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages group membership — listing a group's members, adding, role changes, removal or leaving,
 * and ending every membership when the group itself is deleted. A membership is addressed by its
 * own UID; the group it belongs to is read off the membership, and authorization against that
 * group happens in {@code @groupSecurity}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupMemberService {

    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final TaskAssignmentRelease taskAssignmentRelease;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /**
     * Returns a group's roster, by default the people currently in it.
     *
     * @param groupUid the group's public UID
     * @param status   which memberships to return
     * @return the group's members, each carrying {@code isActive} and {@code dateLeft}
     * @throws ResourceNotFoundException if no active group has that UID
     */
    public List<GroupMemberResponse> getMembers(String groupUid, MembershipStatus status) {
        // Before the listing, and on the active-group finder: deleting a group deactivates every
        // membership, so a FORMER read of one would otherwise answer its whole former household.
        requireGroup(groupUid);
        return membershipsOf(groupUid, status).stream()
                .map(GroupMemberResponse::from)
                .toList();
    }

    /**
     * Returns one membership by its UID.
     *
     * @param memberUid the membership's public UID
     * @return the membership
     * @throws ResourceNotFoundException if no membership has that UID
     */
    public GroupMemberResponse getMember(String memberUid) {
        return GroupMemberResponse.from(requireDetailedMembership(memberUid));
    }

    /**
     * Adds a user to a group with the requested role.
     *
     * @param request      the group, the user to add, and the role to give them
     * @param callerUserId the id of the authenticated caller
     * @return the created membership
     * <p>Somebody who left or was removed is <em>readmitted</em> on their original row rather than
     * refused, exactly as the invite-code path does.
     *
     * @throws DuplicateResourceException if the user is already an active member of the group
     * @throws ResourceNotFoundException  if the user does not exist or has closed their account,
     *                                    or the group does not exist or is deleted
     */
    @Transactional
    public GroupMemberResponse addMember(GroupMemberRequest request, Long callerUserId) {
        String groupUid = request.getGroupUid();
        Group group = requireGroup(groupUid);
        Optional<GroupMember> previous =
                groupMemberRepository.findByUserUidAndGroupUid(request.getUserUid(), groupUid);
        if (previous.filter(m -> Boolean.TRUE.equals(m.getIsActive())).isPresent()) {
            throw new DuplicateResourceException("User is already a member of this group");
        }

        // Active accounts only, and on both branches: a closed one cannot act, so adding or
        // readmitting it would undo what MembershipClosure did on closure and leave the household
        // a member who can never answer - one who could then inherit the last admin's seat.
        User user = userRepository.findByUidAndIsActiveTrue(request.getUserUid())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));

        GroupMember addedBy = actingMember(callerUserId, group.getIdGroup());
        boolean readmitted = previous.isPresent();

        GroupMember member = previous
                .map(existing -> readmit(existing, request.getRole(), addedBy))
                .orElseGet(() -> GroupMember.builder()
                        .uid(UUID.randomUUID().toString())
                        .user(user)
                        .group(group)
                        .role(request.getRole())
                        .isActive(true)
                        .dateJoined(LocalDateTime.now(clock))
                        .addedBy(addedBy)
                        .build());

        GroupMember saved = groupMemberRepository.save(member);
        log.info("Group member added: memberUid={} groupUid={} readmitted={}",
                saved.getUid(), groupUid, readmitted);
        auditLogService.recordEvent(AuditEntityType.GROUP_MEMBER, saved.getIdGroupMember(), AuditAction.MEMBER_ADDED,
                (readmitted ? "Member readmitted with role " : "Member added with role ") + saved.getRole(), group);
        return GroupMemberResponse.from(saved);
    }

    // Revives the row a former member left behind rather than twinning it - the unique
    // (user_id, group_id) pair rules a second one out, so refusing would leave them unaddable for
    // good. GroupService.rejoin is the self-service half of this; it forces GROUP_MEMBER, while an
    // admin readmitting somebody gives the role they asked for.
    private GroupMember readmit(GroupMember existing, GroupRole role, GroupMember addedBy) {
        existing.setIsActive(true);
        existing.setRole(role);
        existing.setDateJoined(LocalDateTime.now(clock));
        existing.setDateLeft(null);
        existing.setRemovedBy(null);
        existing.setAddedBy(addedBy);
        return existing;
    }

    /**
     * Changes a member's role within their group (an authorization-relevant change).
     *
     * @param memberUid the membership's public UID
     * @param role      the new role
     * @return the updated membership
     * @throws ResourceNotFoundException if no membership has that UID, or it is no longer active
     * @throws BusinessRuleException     if this would demote the group's last admin
     */
    @Transactional
    public GroupMemberResponse updateMemberRole(String memberUid, GroupRole role) {
        GroupMember member = requireActiveMembership(memberUid);

        if (member.getRole() == GroupRole.GROUP_ADMIN && role != GroupRole.GROUP_ADMIN
                && isLastAdmin(member)) {
            throw new BusinessRuleException(ErrorMessages.GROUP_NEEDS_AN_ADMIN);
        }

        AuditChanges changes = AuditChanges.create()
                .setVisible("role", member::getRole, member::setRole, role);
        // Authorization-relevant state change → INFO.
        log.info("Group member role changed: memberUid={} role={}", memberUid, role);
        GroupMember saved = groupMemberRepository.save(member);
        auditLogService.recordEvent(AuditEntityType.GROUP_MEMBER, saved.getIdGroupMember(), AuditAction.ROLE_CHANGED,
                "Role changed to " + role, saved.getGroup(), changes);
        return GroupMemberResponse.from(saved);
    }

    /**
     * Removes a membership and stamps its leave time (soft). A caller removing their own
     * membership is leaving the group, which is recorded as a distinct event — the two are the
     * same state change but not the same thing to a reader of the trail.
     *
     * @param memberUid    the membership's public UID
     * @param callerUserId the id of the authenticated caller
     * @return {@code true} if the caller left their own group, {@code false} if they removed someone
     * @throws ResourceNotFoundException if no membership has that UID, or it is already removed
     * @throws BusinessRuleException     if this would remove the group's last admin
     */
    @Transactional
    public boolean removeMember(String memberUid, Long callerUserId) {
        GroupMember member = requireActiveMembership(memberUid);
        boolean left = member.getUser().getIdUser().equals(callerUserId);
        deactivate(member, left ? member : actingMember(callerUserId, member.getGroup().getIdGroup()));
        log.info("Group member {}: memberUid={} groupUid={}", left ? "left" : "removed",
                memberUid, member.getGroup().getUid());
        auditLogService.recordEvent(AuditEntityType.GROUP_MEMBER, member.getIdGroupMember(),
                left ? AuditAction.MEMBER_LEFT : AuditAction.MEMBER_REMOVED,
                left ? "Member left group" : "Member removed from group", member.getGroup());
        return left;
    }

    /**
     * Deactivates every active membership of a group, for when the group itself is being deleted.
     * Deliberately skips the last-admin guard: that rule protects a living group from losing its
     * only admin, and there is no group left to protect.
     *
     * @param group        the group being deleted
     * @param callerUserId the id of the authenticated caller
     * @return how many memberships were deactivated
     */
    @Transactional
    public int deactivateAllForGroup(Group group, Long callerUserId) {
        List<GroupMember> members = groupMemberRepository.findByGroupIdGroupAndIsActiveTrue(group.getIdGroup());
        // Resolved once, before the loop deactivates it too: an admin deleting their own group is
        // recorded as having removed themselves along with everyone else.
        GroupMember actor = actingMember(callerUserId, group.getIdGroup());
        LocalDateTime now = LocalDateTime.now(clock);
        // Assignments are deliberately left alone here: the household itself is going, so no
        // board will read them again - unlike one member leaving a group that carries on.
        for (GroupMember member : members) {
            member.setIsActive(false);
            member.setDateLeft(now);
            member.setRemovedBy(actor);
            groupMemberRepository.save(member);
            auditLogService.recordEvent(AuditEntityType.GROUP_MEMBER, member.getIdGroupMember(),
                    AuditAction.MEMBER_REMOVED, "Membership ended with the group's deletion", group);
        }
        log.info("Group memberships ended with the group: groupUid={} members={}",
                group.getUid(), members.size());
        return members.size();
    }

    // The last-admin guard, then the soft delete. The caller has already proven the membership is
    // active, so the guard only has to ask whether it is the last admin.
    private void deactivate(GroupMember member, GroupMember removedBy) {
        if (member.getRole() == GroupRole.GROUP_ADMIN && isLastAdmin(member)) {
            throw new BusinessRuleException(ErrorMessages.GROUP_NEEDS_AN_ADMIN);
        }
        member.setIsActive(false);
        member.setDateLeft(LocalDateTime.now(clock));
        member.setRemovedBy(removedBy);
        groupMemberRepository.save(member);
        // Their chores go back to the household: a task still assigned to somebody who has
        // left reads as taken care of, and nobody else claims it.
        taskAssignmentRelease.releaseFor(member);
    }

    // Both attribution columns point at a membership, not a user, so a SUPER_ADMIN acting from the
    // platform dashboard has nothing to point at and the column stays null. That is by design.
    private GroupMember actingMember(Long callerUserId, Long groupId) {
        return groupMemberRepository.findByUserIdUserAndGroupIdGroup(callerUserId, groupId)
                .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                .orElse(null);
    }

    // A finder per status rather than one unfiltered read narrowed in memory, which would load
    // rows only to discard them.
    private List<GroupMember> membershipsOf(String groupUid, MembershipStatus status) {
        return switch (status) {
            case FORMER -> groupMemberRepository.findByGroupUidAndIsActiveFalse(groupUid);
            case ALL -> groupMemberRepository.findByGroupUid(groupUid);
            case ACTIVE -> groupMemberRepository.findByGroupUidAndIsActiveTrue(groupUid);
        };
    }

    private Group requireGroup(String groupUid) {
        return groupRepository.findByUidAndIsActiveTrue(groupUid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));
    }

    // Writes target an active membership only. A left or removed one is history: writing to it
    // would set a role nobody holds, or stamp a second departure over the first.
    private GroupMember requireActiveMembership(String memberUid) {
        return groupMemberRepository.findByUid(memberUid)
                .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_MEMBER_NOT_FOUND));
    }

    // Reads stay permissive: the row carries date_left and removed_by, which is the record of who
    // removed whom, and the response is built for exactly that.
    private GroupMember requireDetailedMembership(String memberUid) {
        return groupMemberRepository.findDetailedByUid(memberUid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_MEMBER_NOT_FOUND));
    }

    // Whether the given member is the only remaining active admin of their group.
    private boolean isLastAdmin(GroupMember member) {
        return groupMemberRepository.countByGroupIdGroupAndRoleAndIsActiveTrue(
                member.getGroup().getIdGroup(), GroupRole.GROUP_ADMIN) <= 1;
    }
}
