package com.tasklean.api.domain.tag;

import com.tasklean.api.common.LabelNames;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.group.Group;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the tag names a member typed into the household's tag rows, creating the ones it does not
 * have yet. Its own component because tagging is the one path that writes a tag without anybody
 * managing one: the names arrive on a task, not on the tag endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagResolver {

    private final TagRepository tagRepository;
    private final AuditLogService auditLogService;

    /**
     * Resolves the given names to the household's tags, ordered by name.
     *
     * <p>Names normalise and then match case-insensitively, so a member typing a label the
     * household already has lands on that tag rather than adding a second spelling of it. A name
     * nobody has used becomes a new tag, and one held by a deleted tag revives it.
     *
     * @param group the household the tags belong to
     * @param names the names as the caller sent them, possibly null or empty
     * @return the resolved tags, deduplicated and ordered by name
     */
    @Transactional
    public List<Tag> resolve(Group group, List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        // Keyed by the matching form, so "kitchen" and "Kitchen" in one request resolve once -
        // sent twice they would break the unique constraint on task_tag as a 500.
        Map<String, Tag> resolved = new LinkedHashMap<>();
        for (String raw : names) {
            String name = LabelNames.normalise(raw);
            if (name == null || name.isEmpty()) {
                continue;
            }
            resolved.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> findOrCreate(group, name));
        }
        return resolved.values().stream()
                .sorted(Comparator.comparing(Tag::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private Tag findOrCreate(Group group, String name) {
        Tag existing = tagRepository.findByGroupAndName(group.getIdGroup(), name).orElse(null);
        // The household's own spelling wins. Typing a label onto a task must not rename the tag
        // everybody else sees on theirs, which is what applying the typed casing would do.
        if (existing != null && Boolean.TRUE.equals(existing.getIsActive())) {
            return existing;
        }

        // A deleted tag keeps its name and its task_tag rows, so typing one back revives the
        // original label with whatever it was already attached to.
        Tag tag = existing != null ? existing : Tag.builder().group(group).name(name).build();
        tag.setIsActive(true);

        Tag saved = tagRepository.save(tag);
        String verb = existing != null ? "restored" : "created";
        log.info("Tag created while tagging: id={} groupUid={} revived={}",
                saved.getIdTag(), group.getUid(), existing != null);
        auditLogService.recordEvent(AuditEntityType.TAG, saved.getIdTag(), AuditAction.CREATE,
                "Tag \"" + saved.getName() + "\" " + verb, group);
        return saved;
    }
}
