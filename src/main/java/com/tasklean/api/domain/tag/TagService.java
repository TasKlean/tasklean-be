package com.tasklean.api.domain.tag;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.LabelNames;
import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditChanges;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.tag.dto.TagCreateRequest;
import com.tasklean.api.domain.tag.dto.TagQueryRequest;
import com.tasklean.api.domain.tag.dto.TagResponse;
import com.tasklean.api.domain.tag.dto.TagUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages a household's tags. Same naming rule as categories — unique per household regardless of
 * case, and a deleted name stays reserved so re-creating one revives the original tag. Deleting a
 * tag is allowed while tasks still carry it: the label simply stops being offered or shown.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {

    private static final String NAME_TAKEN = "Tag with this name already exists in the group";

    private final TagRepository tagRepository;
    private final GroupRepository groupRepository;
    private final AuditLogService auditLogService;

    /**
     * Returns a tag by its id.
     *
     * @param id the tag id
     * @return the tag
     * @throws ResourceNotFoundException if no live tag has that id
     */
    public TagResponse getTagById(Long id) {
        return TagResponse.from(live(id));
    }

    /**
     * Returns a household's tags, ordered by name and optionally narrowed by a search — which is
     * what the frontend suggests from as a member types a label.
     *
     * @param query the household and the text to match
     * @return the matching tags
     * @throws ResourceNotFoundException if no active group has that UID
     */
    public List<TagResponse> searchTags(TagQueryRequest query) {
        String q = query.getQ() == null ? "" : query.getQ().trim();
        return tagRepository.searchActive(group(query.getGroupUid()).getIdGroup(), q).stream()
                .map(TagResponse::from)
                .toList();
    }

    /**
     * Creates a tag in a household, or revives the deleted one that already holds the name.
     *
     * @param request the tag's details and the household it belongs to
     * @return the created or revived tag
     * @throws ResourceNotFoundException  if no active group has that UID
     * @throws DuplicateResourceException if the household already has a live tag by that name
     */
    @Transactional
    public TagResponse createTag(TagCreateRequest request) {
        Group group = group(request.getGroupUid());
        String name = LabelNames.normalise(request.getName());

        Tag existing = tagRepository.findByGroupAndName(group.getIdGroup(), name).orElse(null);
        if (existing != null && Boolean.TRUE.equals(existing.getIsActive())) {
            throw new DuplicateResourceException(NAME_TAKEN);
        }

        // A deleted tag keeps its name and its task_tag rows, so re-creating one is a revival -
        // the same label coming back, with whatever it was already attached to.
        Tag tag = existing != null ? existing : Tag.builder().group(group).build();
        tag.setName(name);
        tag.setColor(request.getColor());
        tag.setIsActive(true);

        Tag saved = tagRepository.save(tag);
        log.info("Tag created: id={} groupUid={} revived={}",
                saved.getIdTag(), group.getUid(), existing != null);
        auditLogService.recordEvent(AuditEntityType.TAG, saved.getIdTag(), AuditAction.CREATE,
                auditMessage(saved.getName(), existing != null ? "restored" : "created"), group);
        return TagResponse.from(saved);
    }

    /**
     * Updates a tag's name and color.
     *
     * @param id      the tag id
     * @param request the new details
     * @return the updated tag
     * @throws ResourceNotFoundException  if no live tag has that id
     * @throws DuplicateResourceException if the new name belongs to another tag of the same
     *                                    household
     */
    @Transactional
    public TagResponse updateTag(Long id, TagUpdateRequest request) {
        Tag tag = live(id);
        String name = LabelNames.normalise(request.getName());
        requireNameIsFree(tag, name);

        AuditChanges changes = AuditChanges.create()
                .set("name", tag::getName, tag::setName, name)
                // A presentational token, not anything a person wrote, so its value is kept.
                .setVisible("color", tag::getColor, tag::setColor, request.getColor());

        Tag saved = tagRepository.save(tag);
        auditLogService.recordEvent(AuditEntityType.TAG, saved.getIdTag(), AuditAction.UPDATE,
                auditMessage(saved.getName(), "updated"), saved.getGroup(), changes);
        return TagResponse.from(saved);
    }

    /**
     * Soft-deletes a tag. Its {@code task_tag} rows are left alone — a tag is a loose label, so
     * the tasks carrying it simply stop showing it, and reviving the name brings it all back.
     *
     * @param id the tag id
     * @throws ResourceNotFoundException if no live tag has that id
     */
    @Transactional
    public void deleteTag(Long id) {
        Tag tag = live(id);
        tag.setIsActive(false);
        tagRepository.save(tag);
        log.info("Tag soft-deleted: id={}", id);
        auditLogService.recordEvent(AuditEntityType.TAG, tag.getIdTag(), AuditAction.DELETE,
                auditMessage(tag.getName(), "deleted"), tag.getGroup());
    }

    // Filtered, so a deleted tag is a 404 - its @PreAuthorize resolver keeps the unfiltered
    // lookup, which is what makes a member's "gone" distinct from an outsider's 403.
    private Tag live(Long id) {
        return tagRepository.findByIdTagAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TAG_NOT_FOUND));
    }

    private Group group(String groupUid) {
        return groupRepository.findByUidAndIsActiveTrue(groupUid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));
    }

    // The name may collide with a deleted tag too: the unique index covers those rows, so letting
    // the rename through would surface as a constraint violation rather than a 409.
    private void requireNameIsFree(Tag tag, String name) {
        tagRepository.findByGroupAndName(tag.getGroup().getIdGroup(), name)
                .filter(other -> !other.getIdTag().equals(tag.getIdTag()))
                .ifPresent(other -> {
                    throw new DuplicateResourceException(NAME_TAKEN);
                });
    }

    private static String auditMessage(String name, String verb) {
        return "Tag \"" + name + "\" " + verb;
    }
}
