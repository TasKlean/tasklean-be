package com.tasklean.api.domain.group;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.BusinessRuleException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.group.dto.GroupRequest;
import com.tasklean.api.domain.group.dto.GroupResponse;
import com.tasklean.api.domain.group.dto.MyGroupResponse;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.groupmember.GroupRole;
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
 * Manages groups (households) — lookup by UID, listing, creation, updates, and
 * soft-deletion, plus joining a group by redeeming its invite code. Each group is issued a
 * unique invite code on creation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /**
     * Returns a group by its public UID, as seen by the given caller. The invite code is included
     * only when the caller is an admin of that group.
     *
     * @param uid          the group's public UID
     * @param callerUserId the id of the authenticated caller
     * @return the group
     * @throws ResourceNotFoundException if no group has that UID
     */
    public GroupResponse getGroupByUid(String uid, Long callerUserId) {
        Group group = groupRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));
        return isGroupAdmin(callerUserId, group.getIdGroup())
                ? GroupResponse.withInviteCode(group)
                : GroupResponse.from(group);
    }

    /**
     * Returns all groups, without invite codes — this is a platform-admin listing, and staff have
     * no reason to hold the codes that let someone join a household.
     *
     * @return all groups
     */
    public List<GroupResponse> getAllGroups() {
        return groupRepository.findAll().stream()
                .map(GroupResponse::from)
                .toList();
    }

    /**
     * Returns the groups the given user is an active member of, each carrying that user's own role
     * in the group. This is how a normal user discovers their own households.
     *
     * @param userId the id of the authenticated user
     * @return the user's active memberships as group summaries, newest membership last
     */
    public List<MyGroupResponse> getMyGroups(Long userId) {
        return groupMemberRepository.findByUserIdUserAndIsActiveTrue(userId).stream()
                // A membership can outlive its group's soft-deletion, so filter on the group too.
                .filter(m -> Boolean.TRUE.equals(m.getGroup().getIsActive()))
                .map(MyGroupResponse::from)
                .toList();
    }

    /**
     * Creates a group with a generated UID and a unique invite code, and adds the creator as
     * its first {@code GROUP_ADMIN}.
     *
     * @param request       the group details (name, description, photo)
     * @param creatorUserId the id of the authenticated user creating the group
     * @return the created group
     */
    @Transactional
    public GroupResponse createGroup(GroupRequest request, Long creatorUserId) {
        Group group = Group.builder()
                .uid(UUID.randomUUID().toString())
                .name(request.getName())
                .description(request.getDescription())
                .photoUrl(request.getPhotoUrl())
                .inviteCode(generateInviteCode())
                .isActive(true)
                .build();
        Group saved = groupRepository.save(group);
        log.info("Group created: uid={}", saved.getUid());
        auditLogService.recordEvent(AuditEntityType.GROUP, saved.getIdGroup(), AuditAction.CREATE,
                auditMessage(saved.getName(), "created"), saved);

        // The creator is the group's first admin.
        GroupMember creator = GroupMember.builder()
                .user(userRepository.getReferenceById(creatorUserId))
                .group(saved)
                .role(GroupRole.GROUP_ADMIN)
                .isActive(true)
                .dateJoined(LocalDateTime.now(clock))
                .build();
        GroupMember savedMember = groupMemberRepository.save(creator);

        auditLogService.recordEvent(AuditEntityType.GROUP_MEMBER, savedMember.getIdGroupMember(),
                AuditAction.MEMBER_ADDED, "Creator added as group admin", saved);

        // The creator is the group's admin, so they get the invite code straight away to share.
        return GroupResponse.withInviteCode(saved);
    }

    /**
     * Joins the caller to the group holding the given invite code, as a {@code GROUP_MEMBER}.
     * A user who previously left has their existing membership reactivated rather than duplicated,
     * and does not regain any admin role they once held.
     *
     * @param inviteCode the invite code being redeemed (case-insensitive)
     * @param userId     the id of the authenticated user joining
     * @return the joined group, with the caller's role in it
     * @throws ResourceNotFoundException if no active group holds that invite code
     * @throws BusinessRuleException     if the caller is already an active member
     */
    @Transactional
    public MyGroupResponse joinByInviteCode(String inviteCode, Long userId) {
        // Codes are generated uppercase; accept whatever casing the user typed or pasted.
        String normalized = inviteCode.trim().toUpperCase();
        Group group = groupRepository.findByInviteCode(normalized)
                .filter(g -> Boolean.TRUE.equals(g.getIsActive()))
                // Same error for an unknown and a soft-deleted group
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.INVALID_INVITE_CODE));

        GroupMember membership = groupMemberRepository
                .findByUserIdUserAndGroupIdGroup(userId, group.getIdGroup())
                .map(this::rejoin)
                .orElseGet(() -> GroupMember.builder()
                        .user(userRepository.getReferenceById(userId))
                        .group(group)
                        .role(GroupRole.GROUP_MEMBER)
                        .isActive(true)
                        .dateJoined(LocalDateTime.now(clock))
                        .build());

        GroupMember saved = groupMemberRepository.save(membership);
        log.info("User joined group via invite code: groupUid={} groupMemberId={}",
                group.getUid(), saved.getIdGroupMember());
        auditLogService.recordEvent(AuditEntityType.GROUP_MEMBER, saved.getIdGroupMember(),
                AuditAction.MEMBER_ADDED, "Member joined via invite code", group);
        return MyGroupResponse.from(saved);
    }

    // Reactivates a membership the user previously left. The unique (user_id, group_id) constraint
    // rules out a second row, and rejoining must not restore a role they held before leaving.
    private GroupMember rejoin(GroupMember existing) {
        if (Boolean.TRUE.equals(existing.getIsActive())) {
            throw new BusinessRuleException(ErrorMessages.ALREADY_GROUP_MEMBER);
        }
        existing.setIsActive(true);
        existing.setRole(GroupRole.GROUP_MEMBER);
        existing.setDateJoined(LocalDateTime.now(clock));
        existing.setDateLeft(null);
        existing.setRemovedBy(null);
        return existing;
    }

    /**
     * Updates a group's name, description, and photo.
     *
     * @param uid     the group's public UID
     * @param request the new group details
     * @return the updated group
     * @throws ResourceNotFoundException if no group has that UID
     */
    @Transactional
    public GroupResponse updateGroup(String uid, GroupRequest request) {
        Group group = groupRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));
        group.setName(request.getName());
        group.setDescription(request.getDescription());
        group.setPhotoUrl(request.getPhotoUrl());
        Group saved = groupRepository.save(group);
        auditLogService.recordEvent(AuditEntityType.GROUP, saved.getIdGroup(), AuditAction.UPDATE,
                auditMessage(saved.getName(), "updated"), saved);
        // Only a group admin (or SUPER_ADMIN) can reach this endpoint, so the code is safe to return.
        return GroupResponse.withInviteCode(saved);
    }

    /**
     * Soft-deletes a group (sets it inactive).
     *
     * @param uid the group's public UID
     * @throws ResourceNotFoundException if no group has that UID
     */
    @Transactional
    public void deleteGroup(String uid) {
        Group group = groupRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));
        group.setIsActive(false);
        groupRepository.save(group);
        log.info("Group soft-deleted: uid={}", uid);
        auditLogService.recordEvent(AuditEntityType.GROUP, group.getIdGroup(), AuditAction.DELETE,
                auditMessage(group.getName(), "deleted"), group);
    }

    private static String auditMessage(String name, String verb) {
        return "Group \"" + name + "\" " + verb;
    }

    // Mirrors @groupSecurity.isAdminOfGroup, but resolves the role from an explicit user id rather
    // than the SecurityContext, so the service stays usable outside a request (jobs, tests).
    private boolean isGroupAdmin(Long userId, Long groupId) {
        return userId != null && groupMemberRepository.findByUserIdUserAndGroupIdGroup(userId, groupId)
                .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                .map(m -> m.getRole() == GroupRole.GROUP_ADMIN)
                .orElse(false);
    }

    private String generateInviteCode() {
        String code;
        // Retry until the random 8-char code is unique across existing groups.
        do {
            code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (groupRepository.existsByInviteCode(code));
        return code;
    }
}
