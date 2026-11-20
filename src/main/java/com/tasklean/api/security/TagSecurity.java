package com.tasklean.api.security;

import com.tasklean.api.domain.tag.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;

/**
 * Authorization for a single tag, referenced as {@code @tagSecurity} from {@code @PreAuthorize}.
 * Same shape as {@link CategorySecurity}: resolve the household behind the row, then ask
 * {@link GroupSecurity} about it, reading the group id alone so a proxy can serve it.
 */
@Component("tagSecurity")
@RequiredArgsConstructor
public class TagSecurity {

    private final TagRepository tagRepository;
    private final GroupSecurity groupSecurity;

    /**
     * Whether the caller shares this tag's household. It gates reading one and also editing one,
     * because setting a color is any member's — a rename inside the same body is refused by
     * {@code TagService}, which is where a rule about one field has to live.
     *
     * @param id the tag id
     * @return {@code true} if the caller shares the household
     */
    public boolean canView(Long id) {
        return onTagsGroup(id, groupSecurity::isMemberOfGroup);
    }

    /**
     * Whether the caller may delete this tag. Creating one is open to any member, but removing it
     * takes the label off every task already carrying it, so that much is a group-admin act — as
     * is renaming one, which {@code TagService} enforces for the same reason.
     *
     * @param id the tag id
     * @return {@code true} if the caller administers its household
     */
    public boolean canManage(Long id) {
        return onTagsGroup(id, groupSecurity::isAdminOfGroup);
    }

    private boolean onTagsGroup(Long id, Predicate<Long> check) {
        return id != null && tagRepository.findById(id)
                .map(tag -> check.test(tag.getGroup().getIdGroup()))
                .orElse(false);
    }
}
