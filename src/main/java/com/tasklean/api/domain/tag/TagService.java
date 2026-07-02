package com.tasklean.api.domain.tag;

import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.tag.dto.TagRequest;
import com.tasklean.api.domain.tag.dto.TagResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final GroupRepository groupRepository;

    public TagResponse getTagById(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found"));
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
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        if (tagRepository.existsByGroupIdGroupAndName(request.getGroupId(), request.getName())) {
            throw new DuplicateResourceException("Tag with this name already exists in the group");
        }

        Tag tag = Tag.builder()
                .name(request.getName())
                .color(request.getColor())
                .isActive(true)
                .group(group)
                .build();
        return TagResponse.from(tagRepository.save(tag));
    }

    @Transactional
    public TagResponse updateTag(Long id, TagRequest request) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found"));
        tag.setName(request.getName());
        tag.setColor(request.getColor());
        return TagResponse.from(tagRepository.save(tag));
    }

    @Transactional
    public void deleteTag(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found"));
        tag.setIsActive(false);
        tagRepository.save(tag);
    }
}
