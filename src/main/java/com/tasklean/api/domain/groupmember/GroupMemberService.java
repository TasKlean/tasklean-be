package com.tasklean.api.domain.groupmember;

import com.tasklean.api.common.ErrorMessages;
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

/**
 * Manages group membership — adding members, role changes, lookups, and removal.
 * A user can belong to a given group only once.
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
     * Returns a group membership by its id.
     *
     * @param id the membership id
     * @return the membership
     * @throws ResourceNotFoundException if no membership has that id
     */
    public GroupMemberResponse getMemberById(Long id) {
        GroupMember member = groupMemberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_MEMBER_NOT_FOUND));
        return GroupMemberResponse.from(member);
    }

    /**
     * Returns the active members of a group.
     *
     * @param groupId the group id
     * @return the group's active members
     */
    public List<GroupMemberResponse> getMembersByGroup(Long groupId) {
        return groupMemberRepository.findByGroupIdGroupAndIsActiveTrue(groupId).stream()
                .map(GroupMemberResponse::from)
                .toList();
    }

    /**
     * Returns a user's active memberships (the groups they belong to).
     *
     * @param userId the user id
     * @return the user's active memberships
     */
    public List<GroupMemberResponse> getGroupsByUser(Long userId) {
        return groupMemberRepository.findByUserIdUserAndIsActiveTrue(userId).stream()
                .map(GroupMemberResponse::from)
                .toList();
    }

    /**
     * Adds a user to a group with the requested role.
     *
     * @param request the membership details (user, group, role)
     * @return the created membership
     * @throws DuplicateResourceException if the user is already a member of the group
     * @throws ResourceNotFoundException  if the user or group does not exist
     */
    @Transactional
    public GroupMemberResponse addMember(GroupMemberRequest request) {
        if (groupMemberRepository.existsByUserIdUserAndGroupIdGroup(request.getUserId(), request.getGroupId())) {
            throw new DuplicateResourceException("User is already a member of this group");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));
        Group group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));

        GroupMember member = GroupMember.builder()
                .user(user)
                .group(group)
                .role(request.getRole())
                .isActive(true)
                .dateJoined(LocalDateTime.now(clock))
                .build();
        GroupMember saved = groupMemberRepository.save(member);
        log.info("Group member added: id={} user={} group={}", saved.getIdGroupMember(), user.getIdUser(), group.getIdGroup());
        auditLogService.recordEvent(AuditEntityType.GROUP_MEMBER, saved.getIdGroupMember(), AuditAction.MEMBER_ADDED,
                "Member added with role " + saved.getRole(), group);
        return GroupMemberResponse.from(saved);
    }

    /**
     * Changes a member's role (an authorization-relevant change).
     *
     * @param id   the membership id
     * @param role the new role
     * @return the updated membership
     * @throws ResourceNotFoundException if no membership has that id
     */
    @Transactional
    public GroupMemberResponse updateMemberRole(Long id, String role) {
        GroupMember member = groupMemberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_MEMBER_NOT_FOUND));
        member.setRole(role);
        // Authorization-relevant state change → INFO.
        log.info("Group member role changed: id={} role={}", id, role);
        GroupMember saved = groupMemberRepository.save(member);
        auditLogService.recordEvent(AuditEntityType.GROUP_MEMBER, saved.getIdGroupMember(), AuditAction.ROLE_CHANGED,
                "Role changed to " + role, saved.getGroup());
        return GroupMemberResponse.from(saved);
    }

    /**
     * Soft-removes a member from a group and stamps their leave time.
     *
     * @param id the membership id
     * @throws ResourceNotFoundException if no membership has that id
     */
    @Transactional
    public void removeMember(Long id) {
        GroupMember member = groupMemberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_MEMBER_NOT_FOUND));
        member.setIsActive(false);
        member.setDateLeft(LocalDateTime.now(clock));
        groupMemberRepository.save(member);
        log.info("Group member removed: id={}", id);
        auditLogService.recordEvent(AuditEntityType.GROUP_MEMBER, member.getIdGroupMember(), AuditAction.MEMBER_REMOVED,
                "Member removed from group", member.getGroup());
    }
}
