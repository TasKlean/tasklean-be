package com.tasklean.api.domain.groupmember;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.dto.GroupMemberRequest;
import com.tasklean.api.domain.groupmember.dto.GroupMemberResponse;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupMemberService {

    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final Clock clock;

    public GroupMemberResponse getMemberById(Long id) {
        GroupMember member = groupMemberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_MEMBER_NOT_FOUND));
        return GroupMemberResponse.from(member);
    }

    public List<GroupMemberResponse> getMembersByGroup(Long groupId) {
        return groupMemberRepository.findByGroupIdGroupAndIsActiveTrue(groupId).stream()
                .map(GroupMemberResponse::from)
                .toList();
    }

    public List<GroupMemberResponse> getGroupsByUser(Long userId) {
        return groupMemberRepository.findByUserIdUserAndIsActiveTrue(userId).stream()
                .map(GroupMemberResponse::from)
                .toList();
    }

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
        return GroupMemberResponse.from(groupMemberRepository.save(member));
    }

    @Transactional
    public GroupMemberResponse updateMemberRole(Long id, String role) {
        GroupMember member = groupMemberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_MEMBER_NOT_FOUND));
        member.setRole(role);
        return GroupMemberResponse.from(groupMemberRepository.save(member));
    }

    @Transactional
    public void removeMember(Long id) {
        GroupMember member = groupMemberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_MEMBER_NOT_FOUND));
        member.setIsActive(false);
        member.setDateLeft(LocalDateTime.now(clock));
        groupMemberRepository.save(member);
    }
}
