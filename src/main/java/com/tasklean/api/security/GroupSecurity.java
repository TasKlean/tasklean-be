package com.tasklean.api.security;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.groupmember.GroupRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Relationship-based (group-scoped) authorization checks referenced from {@code @PreAuthorize}
 * expressions (as {@code @groupSecurity}). Resolves the caller's active membership of the target
 * group and its role.
 */
@Component("groupSecurity")
@RequiredArgsConstructor
public class GroupSecurity {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;

    /**
     * Whether the caller is an active member of the group with the given public UID.
     *
     * @param groupUid the target group's public UID
     * @return {@code true} if the caller is an active member
     */
    public boolean isMember(String groupUid) {
        Optional<Long> groupId = groupRepository.findByUid(groupUid).map(Group::getIdGroup);
        return groupId.isPresent() && isMemberOfGroup(groupId.get());
    }

    /**
     * Whether the caller is an active admin of the group with the given public UID.
     *
     * @param groupUid the target group's public UID
     * @return {@code true} if the caller is an active {@code GROUP_ADMIN}
     */
    public boolean isAdmin(String groupUid) {
        Optional<Long> groupId = groupRepository.findByUid(groupUid).map(Group::getIdGroup);
        return groupId.isPresent() && isAdminOfGroup(groupId.get());
    }

    /**
     * Whether the caller is an active member of the group with the given id.
     *
     * @param groupId the target group id
     * @return {@code true} if the caller is an active member
     */
    public boolean isMemberOfGroup(Long groupId) {
        return membership(groupId).isPresent();
    }

    /**
     * Whether the caller is an active admin of the group with the given id.
     *
     * @param groupId the target group id
     * @return {@code true} if the caller is an active {@code GROUP_ADMIN}
     */
    public boolean isAdminOfGroup(Long groupId) {
        return membership(groupId)
                .map(m -> m.getRole() == GroupRole.GROUP_ADMIN)
                .orElse(false);
    }

    /**
     * Whether the caller may view the given membership — i.e. is an active member of that
     * membership's group.
     *
     * @param groupMemberId the target membership id
     * @return {@code true} if the caller shares the group
     */
    public boolean canViewMember(Long groupMemberId) {
        return groupMemberRepository.findById(groupMemberId)
                .map(target -> isMemberOfGroup(target.getGroup().getIdGroup()))
                .orElse(false);
    }

    /**
     * Whether the caller may manage the given membership (change role, remove) — i.e. is an active
     * admin of that membership's group.
     *
     * @param groupMemberId the target membership id
     * @return {@code true} if the caller is an admin of the membership's group
     */
    public boolean canManageMember(Long groupMemberId) {
        return groupMemberRepository.findById(groupMemberId)
                .map(target -> isAdminOfGroup(target.getGroup().getIdGroup()))
                .orElse(false);
    }

    /**
     * Whether the given membership belongs to the caller (used to allow leaving a group).
     *
     * @param groupMemberId the target membership id
     * @return {@code true} if the membership is the caller's own
     */
    public boolean isSelfMember(Long groupMemberId) {
        Long userId = currentUserId();
        return userId != null && groupMemberRepository.findById(groupMemberId)
                .map(target -> userId.equals(target.getUser().getIdUser()))
                .orElse(false);
    }

    private Optional<GroupMember> membership(Long groupId) {
        Long userId = currentUserId();
        if (userId == null || groupId == null) {
            return Optional.empty();
        }
        return groupMemberRepository.findByUserIdUserAndGroupIdGroup(userId, groupId)
                .filter(m -> Boolean.TRUE.equals(m.getIsActive()));
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthPrincipal principal ? principal.userId() : null;
    }
}
