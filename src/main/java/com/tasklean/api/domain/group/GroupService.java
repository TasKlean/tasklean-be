package com.tasklean.api.domain.group;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.group.dto.GroupRequest;
import com.tasklean.api.domain.group.dto.GroupResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;

    public GroupResponse getGroupByUid(String uid) {
        Group group = groupRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));
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
        Group saved = groupRepository.save(group);
        log.info("Group created: uid={}", saved.getUid());
        return GroupResponse.from(saved);
    }

    @Transactional
    public GroupResponse updateGroup(String uid, GroupRequest request) {
        Group group = groupRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));
        group.setName(request.getName());
        group.setDescription(request.getDescription());
        group.setPhotoUrl(request.getPhotoUrl());
        return GroupResponse.from(groupRepository.save(group));
    }

    @Transactional
    public void deleteGroup(String uid) {
        Group group = groupRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));
        group.setIsActive(false);
        groupRepository.save(group);
        log.info("Group soft-deleted: uid={}", uid);
    }

    private String generateInviteCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (groupRepository.existsByInviteCode(code));
        return code;
    }
}
