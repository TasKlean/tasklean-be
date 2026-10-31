package com.tasklean.api.domain.groupmember;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.BusinessRuleException;
import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.dto.GroupMemberRequest;
import com.tasklean.api.domain.groupmember.dto.GroupMemberResponse;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Manages group membership — listing a group's members, adding, role changes, and removal or
 * leaving. A membership is addressed by its own UID; the group it belongs to is read off the
 * membership, and authorization against that group happens in {@code @groupSecurity}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupMemberService {

    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /**
     * Returns a group's active members.
     *
     * @param groupUid the group's public UID
     * @return the group's active members
     * @throws ResourceNotFoundException if no group has that UID
     */
    public List<GroupMemberResponse> getMembers(String groupUid) {
        requireGroup(groupUid);
        return groupMemberRepository.findByGroupUidAndIsActiveTrue(groupUid).stream()
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
        return GroupMemberResponse.from(requireMembership(memberUid));
    }

    /**
     * Adds a user to a group with the requested role.
     *
     * @param request the group, the user to add, and the role to give them
     * @return the created membership
     * @throws DuplicateResourceException if the user already has a membership of the group
     * @throws ResourceNotFoundException  if the group or the user does not exist
     */
    @Transactional
    public GroupMemberResponse addMember(GroupMemberRequest request) {
        String groupUid = request.getGroupUid();
        Group group = requireGroup(groupUid);
        if (groupMemberRepository.findByUserUidAndGroupUid(request.getUserUid(), groupUid).isPresent()) {
            throw new DuplicateResourceException("User is already a member of this group");
        }

        User user = userRepository.findByUid(request.getUserUid())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));

        GroupMember member = GroupMember.builder()
                .uid(UUID.randomUUID().toString())
                .user(user)
                .group(group)
                .role(request.getRole())
                .isActive(true)
                .dateJoined(LocalDateTime.now(clock))
                .build();
        GroupMember saved = groupMemberRepository.save(member);
        log.info("Group member added: memberUid={} groupUid={}", saved.getUid(), groupUid);
        auditLogService.recordEvent(AuditEntityType.GROUP_MEMBER, saved.getIdGroupMember(), AuditAction.MEMBER_ADDED,
                "Member added with role " + saved.getRole(), group);
        return GroupMemberResponse.from(saved);
    }

    /**
     * Changes a member's role within their group (an authorization-relevant change).
     *
     * @param memberUid the membership's public UID
     * @param role      the new role
     * @return the updated membership
     * @throws ResourceNotFoundException if no membership has that UID
     * @throws BusinessRuleException     if this would demote the group's last admin
     */
    @Transactional
    public GroupMemberResponse updateMemberRole(String memberUid, GroupRole role) {
        GroupMember member = requireMembership(memberUid);

        if (member.getRole() == GroupRole.GROUP_ADMIN && role != GroupRole.GROUP_ADMIN
                && Boolean.TRUE.equals(member.getIsActive()) && isLastAdmin(member)) {
            throw new BusinessRuleException(ErrorMessages.GROUP_NEEDS_AN_ADMIN);
        }

        member.setRole(role);
        // Authorization-relevant state change → INFO.
        log.info("Group member role changed: memberUid={} role={}", memberUid, role);
        GroupMember saved = groupMemberRepository.save(member);
        auditLogService.recordEvent(AuditEntityType.GROUP_MEMBER, saved.getIdGroupMember(), AuditAction.ROLE_CHANGED,
                "Role changed to " + role, saved.getGroup());
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
     * @throws ResourceNotFoundException if no membership has that UID
     * @throws BusinessRuleException     if this would remove the group's last admin
     */
    @Transactional
    public boolean removeMember(String memberUid, Long callerUserId) {
        GroupMember member = requireMembership(memberUid);
        boolean left = member.getUser().getIdUser().equals(callerUserId);
        deactivate(member);
        log.info("Group member {}: memberUid={} groupUid={}", left ? "left" : "removed",
                memberUid, member.getGroup().getUid());
        auditLogService.recordEvent(AuditEntityType.GROUP_MEMBER, member.getIdGroupMember(),
                left ? AuditAction.MEMBER_LEFT : AuditAction.MEMBER_REMOVED,
                left ? "Member left group" : "Member removed from group", member.getGroup());
        return left;
    }

    // The last-admin guard, then the soft delete.
    private void deactivate(GroupMember member) {
        if (member.getRole() == GroupRole.GROUP_ADMIN && Boolean.TRUE.equals(member.getIsActive())
                && isLastAdmin(member)) {
            throw new BusinessRuleException(ErrorMessages.GROUP_NEEDS_AN_ADMIN);
        }
        member.setIsActive(false);
        member.setDateLeft(LocalDateTime.now(clock));
        groupMemberRepository.save(member);
    }

    private Group requireGroup(String groupUid) {
        return groupRepository.findByUid(groupUid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));
    }

    private GroupMember requireMembership(String memberUid) {
        return groupMemberRepository.findByUid(memberUid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_MEMBER_NOT_FOUND));
    }

    // Whether the given member is the only remaining active admin of their group.
    private boolean isLastAdmin(GroupMember member) {
        return groupMemberRepository.countByGroupIdGroupAndRoleAndIsActiveTrue(
                member.getGroup().getIdGroup(), GroupRole.GROUP_ADMIN) <= 1;
    }
}
