package com.tasklean.api.security;

import com.tasklean.api.domain.category.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;

/**
 * Authorization for a single category, referenced as {@code @categorySecurity} from
 * {@code @PreAuthorize}. A category is addressed by id rather than a uid, so the household behind
 * it is resolved from the row and then handed to {@link GroupSecurity}.
 *
 * <p>Reads the group id only, which a lazy proxy serves without a query — an expression runs
 * before the call, outside any transaction, and {@code spring.jpa.open-in-view} is off.
 */
@Component("categorySecurity")
@RequiredArgsConstructor
public class CategorySecurity {

    private final CategoryRepository categoryRepository;
    private final GroupSecurity groupSecurity;

    /**
     * Whether the caller may read this category — i.e. is a member of its household.
     *
     * @param id the category id
     * @return {@code true} if the caller shares the household
     */
    public boolean canView(Long id) {
        return onCategorysGroup(id, groupSecurity::isMemberOfGroup);
    }

    /**
     * Whether the caller may change or delete this category. A category is the household's own
     * filing system rather than one member's, so editing it is a group-admin act — unlike a tag,
     * which anybody may add.
     *
     * @param id the category id
     * @return {@code true} if the caller administers its household
     */
    public boolean canManage(Long id) {
        return onCategorysGroup(id, groupSecurity::isAdminOfGroup);
    }

    // Unfiltered, like every other resolver here: a deleted category still resolves, so a member
    // reaches the service and gets a 404 while an outsider is stopped with a 403.
    private boolean onCategorysGroup(Long id, Predicate<Long> check) {
        return id != null && categoryRepository.findById(id)
                .map(category -> check.test(category.getGroup().getIdGroup()))
                .orElse(false);
    }
}
