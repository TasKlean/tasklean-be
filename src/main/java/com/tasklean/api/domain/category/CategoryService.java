package com.tasklean.api.domain.category;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.LabelNames;
import com.tasklean.api.common.exception.BusinessRuleException;
import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditChanges;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.category.dto.CategoryCreateRequest;
import com.tasklean.api.domain.category.dto.CategoryResponse;
import com.tasklean.api.domain.category.dto.CategoryUpdateRequest;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.task.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages a household's task categories. A name is unique per household regardless of case, and a
 * deleted name stays reserved — re-creating one brings the original category back rather than
 * adding a second by the same name.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final String NAME_TAKEN = "Category with this name already exists in the group";

    private final CategoryRepository categoryRepository;
    private final GroupRepository groupRepository;
    private final TaskRepository taskRepository;
    private final AuditLogService auditLogService;

    /**
     * Returns a category by its id.
     *
     * @param id the category id
     * @return the category
     * @throws ResourceNotFoundException if no live category has that id
     */
    public CategoryResponse getCategoryById(Long id) {
        return CategoryResponse.from(live(id));
    }

    /**
     * Returns a household's categories, ordered by name.
     *
     * @param groupUid the household's public UID
     * @return its active categories
     * @throws ResourceNotFoundException if no active group has that UID
     */
    public List<CategoryResponse> getCategoriesByGroup(String groupUid) {
        return categoryRepository.findByGroupIdGroupAndIsActiveTrueOrderByName(group(groupUid).getIdGroup())
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    /**
     * Creates a category in a household, or revives the deleted one that already holds the name.
     *
     * @param request the category's details and the household it belongs to
     * @return the created or revived category
     * @throws ResourceNotFoundException  if no active group has that UID
     * @throws DuplicateResourceException if the household already has a live category by that name
     */
    @Transactional
    public CategoryResponse createCategory(CategoryCreateRequest request) {
        Group group = group(request.getGroupUid());
        String name = LabelNames.normalise(request.getName());

        Category existing = categoryRepository.findByGroupAndName(group.getIdGroup(), name).orElse(null);
        if (existing != null && Boolean.TRUE.equals(existing.getIsActive())) {
            throw new DuplicateResourceException(NAME_TAKEN);
        }

        // A deleted category keeps its name and its tasks, so re-creating one is a revival: a new
        // row could not be inserted anyway, and would orphan every task still classified by it.
        Category category = existing != null ? existing : Category.builder().group(group).build();
        category.setName(name);
        category.setColor(request.getColor());
        category.setIcon(request.getIcon());
        category.setIsActive(true);

        Category saved = categoryRepository.save(category);
        log.info("Category created: id={} groupUid={} revived={}",
                saved.getIdCategory(), group.getUid(), existing != null);
        auditLogService.recordEvent(AuditEntityType.CATEGORY, saved.getIdCategory(), AuditAction.CREATE,
                auditMessage(saved.getName(), existing != null ? "restored" : "created"), group);
        return CategoryResponse.from(saved);
    }

    /**
     * Updates a category's name, color and icon.
     *
     * @param id      the category id
     * @param request the new details
     * @return the updated category
     * @throws ResourceNotFoundException  if no live category has that id
     * @throws DuplicateResourceException if the new name belongs to another category of the same
     *                                    household
     */
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryUpdateRequest request) {
        Category category = live(id);
        String name = LabelNames.normalise(request.getName());
        requireNameIsFree(category, name);

        AuditChanges changes = AuditChanges.create()
                .set("name", category::getName, category::setName, name)
                // Presentational tokens, not anything a person wrote, so their values are kept.
                .setVisible("color", category::getColor, category::setColor, request.getColor())
                .setVisible("icon", category::getIcon, category::setIcon, request.getIcon());

        Category saved = categoryRepository.save(category);
        auditLogService.recordEvent(AuditEntityType.CATEGORY, saved.getIdCategory(), AuditAction.UPDATE,
                auditMessage(saved.getName(), "updated"), saved.getGroup(), changes);
        return CategoryResponse.from(saved);
    }

    /**
     * Soft-deletes a category the household no longer files anything under.
     *
     * @param id the category id
     * @throws ResourceNotFoundException if no live category has that id
     * @throws BusinessRuleException     if tasks are still classified under it
     */
    @Transactional
    public void deleteCategory(Long id) {
        Category category = live(id);

        // Refused rather than cleared: un-categorising somebody's chores as a side effect of
        // tidying the list is worse than being told which ones are in the way.
        long inUse = taskRepository.countActiveByCategory(id);
        if (inUse > 0) {
            throw new BusinessRuleException(
                    "This category is still used by " + inUse + " task(s) - move them first");
        }

        category.setIsActive(false);
        categoryRepository.save(category);
        log.info("Category soft-deleted: id={}", id);
        auditLogService.recordEvent(AuditEntityType.CATEGORY, category.getIdCategory(), AuditAction.DELETE,
                auditMessage(category.getName(), "deleted"), category.getGroup());
    }

    // Filtered, so a deleted category is a 404 - its @PreAuthorize resolver keeps the unfiltered
    // lookup, which is what makes a member's "gone" distinct from an outsider's 403.
    private Category live(Long id) {
        return categoryRepository.findByIdCategoryAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.CATEGORY_NOT_FOUND));
    }

    private Group group(String groupUid) {
        return groupRepository.findByUidAndIsActiveTrue(groupUid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));
    }

    // The name may collide with a deleted category too: the unique index covers those rows, so
    // letting the rename through would surface as a constraint violation rather than a 409.
    private void requireNameIsFree(Category category, String name) {
        categoryRepository.findByGroupAndName(category.getGroup().getIdGroup(), name)
                .filter(other -> !other.getIdCategory().equals(category.getIdCategory()))
                .ifPresent(other -> {
                    throw new DuplicateResourceException(NAME_TAKEN);
                });
    }

    private static String auditMessage(String name, String verb) {
        return "Category \"" + name + "\" " + verb;
    }
}
