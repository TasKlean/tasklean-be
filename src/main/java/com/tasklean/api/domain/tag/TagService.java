package com.tasklean.api.domain.tag;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.tag.dto.TagRequest;
import com.tasklean.api.domain.tag.dto.TagResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages tags within a group — lookup, listing, creation, updates, and
 * soft-deletion. Tag names are unique per group.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final GroupRepository groupRepository;
    private final AuditLogService auditLogService;

    /**
     * Returns a tag by its id.
     *
     * @param id the tag id
     * @return the tag
     * @throws ResourceNotFoundException if no tag has that id
     */
    public TagResponse getTagById(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TAG_NOT_FOUND));
        return TagResponse.from(tag);
    }

    /**
     * Returns all active tags for a group.
     *
     * @param groupId the group id
     * @return the group's active tags
     */
    public List<TagResponse> getTagsByGroup(Long groupId) {
        return tagRepository.findByGroupIdGroupAndIsActiveTrue(groupId).stream()
                .map(TagResponse::from)
                .toList();
    }

    /**
     * Creates a tag in a group.
     *
     * @param request the tag details (name, color, group)
     * @return the created tag
     * @throws ResourceNotFoundException  if the group does not exist
     * @throws DuplicateResourceException if the name is already used in that group
     */
    @Transactional
    public TagResponse createTag(TagRequest request) {
        Group group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));

        if (tagRepository.existsByGroupIdGroupAndName(request.getGroupId(), request.getName())) {
            throw new DuplicateResourceException("Tag with this name already exists in the group");
        }

        Tag tag = Tag.builder()
                .name(request.getName())
                .color(request.getColor())
                .isActive(true)
                .group(group)
                .build();
        Tag saved = tagRepository.save(tag);
        log.info("Tag created: id={} group={}", saved.getIdTag(), group.getIdGroup());
        auditLogService.record(AuditEntityType.TAG, saved.getIdTag(), AuditAction.CREATE,
                "Tag \"" + saved.getName() + "\" created", group);
        return TagResponse.from(saved);
    }

    /**
     * Updates a tag's name and color.
     *
     * @param id      the tag id
     * @param request the new tag details
     * @return the updated tag
     * @throws ResourceNotFoundException if no tag has that id
     */
    @Transactional
    public TagResponse updateTag(Long id, TagRequest request) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TAG_NOT_FOUND));
        tag.setName(request.getName());
        tag.setColor(request.getColor());
        Tag saved = tagRepository.save(tag);
        auditLogService.record(AuditEntityType.TAG, saved.getIdTag(), AuditAction.UPDATE,
                "Tag \"" + saved.getName() + "\" updated", saved.getGroup());
        return TagResponse.from(saved);
    }

    /**
     * Soft-deletes a tag (sets it inactive).
     *
     * @param id the tag id
     * @throws ResourceNotFoundException if no tag has that id
     */
    @Transactional
    public void deleteTag(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TAG_NOT_FOUND));
        tag.setIsActive(false);
        tagRepository.save(tag);
        log.info("Tag soft-deleted: id={}", id);
        auditLogService.record(AuditEntityType.TAG, tag.getIdTag(), AuditAction.DELETE,
                "Tag \"" + tag.getName() + "\" deleted", tag.getGroup());
    }
}
