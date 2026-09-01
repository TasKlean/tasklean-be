package com.tasklean.api.domain.tag;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.tag.dto.TagRequest;
import com.tasklean.api.domain.tag.dto.TagResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final GroupRepository groupRepository;

    public TagResponse getTagById(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TAG_NOT_FOUND));
        return TagResponse.from(tag);
    }

    public List<TagResponse> getTagsByGroup(Long groupId) {
        return tagRepository.findByGroupIdGroupAndIsActiveTrue(groupId).stream()
                .map(TagResponse::from)
                .toList();
    }

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
        return TagResponse.from(saved);
    }

    @Transactional
    public TagResponse updateTag(Long id, TagRequest request) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TAG_NOT_FOUND));
        tag.setName(request.getName());
        tag.setColor(request.getColor());
        return TagResponse.from(tagRepository.save(tag));
    }

    @Transactional
    public void deleteTag(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TAG_NOT_FOUND));
        tag.setIsActive(false);
        tagRepository.save(tag);
        log.info("Tag soft-deleted: id={}", id);
    }
}
