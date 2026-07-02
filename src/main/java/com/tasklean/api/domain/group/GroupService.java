package com.tasklean.api.domain.group;

import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.group.dto.GroupRequest;
import com.tasklean.api.domain.group.dto.GroupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;

    public GroupResponse getGroupByUid(String uid) {
        Group group = groupRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        return GroupResponse.from(group);
    }

    public List<GroupResponse> getAllGroups() {
        return groupRepository.findAll().stream()
                .map(GroupResponse::from)
                .toList();
    }

    @Transactional
    public GroupResponse createGroup(GroupRequest request) {
        Group group = Group.builder()
                .uid(UUID.randomUUID().toString())
                .name(request.getName())
                .description(request.getDescription())
                .photoUrl(request.getPhotoUrl())
                .inviteCode(generateInviteCode())
                .isActive(true)
                .build();
        return GroupResponse.from(groupRepository.save(group));
    }

    @Transactional
    public GroupResponse updateGroup(String uid, GroupRequest request) {
        Group group = groupRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        group.setName(request.getName());
        group.setDescription(request.getDescription());
        group.setPhotoUrl(request.getPhotoUrl());
        return GroupResponse.from(groupRepository.save(group));
    }

    @Transactional
    public void deleteGroup(String uid) {
        Group group = groupRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        group.setIsActive(false);
        groupRepository.save(group);
    }

    private String generateInviteCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (groupRepository.existsByInviteCode(code));
        return code;
    }
}
