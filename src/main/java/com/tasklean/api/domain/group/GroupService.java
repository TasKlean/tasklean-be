package com.tasklean.api.domain.group;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.group.dto.GroupRequest;
import com.tasklean.api.domain.group.dto.GroupResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Manages groups (households) — lookup by UID, listing, creation, updates, and
 * soft-deletion. Each group is issued a unique invite code on creation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final AuditLogService auditLogService;

    /**
     * Returns a group by its public UID.
     *
     * @param uid the group's public UID
     * @return the group
     * @throws ResourceNotFoundException if no group has that UID
     */
    public GroupResponse getGroupByUid(String uid) {
        Group group = groupRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));
        return GroupResponse.from(group);
    }

    /**
     * Returns all groups.
     *
     * @return all groups
     */
    public List<GroupResponse> getAllGroups() {
        return groupRepository.findAll().stream()
                .map(GroupResponse::from)
                .toList();
    }

    /**
     * Creates a group with a generated UID and a unique invite code.
     *
     * @param request the group details (name, description, photo)
     * @return the created group
     */
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
        auditLogService.recordEvent(AuditEntityType.GROUP, saved.getIdGroup(), AuditAction.CREATE,
                auditMessage(saved.getName(), "created"), saved);
        return GroupResponse.from(saved);
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
        return GroupResponse.from(saved);
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

    private String generateInviteCode() {
        String code;
        // Retry until the random 8-char code is unique across existing groups.
        do {
            code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (groupRepository.existsByInviteCode(code));
        return code;
    }
}
