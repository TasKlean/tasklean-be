package com.tasklean.api.domain.category;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.category.dto.CategoryRequest;
import com.tasklean.api.domain.category.dto.CategoryResponse;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages task categories within a group — lookup, listing, creation, updates,
 * and soft-deletion. Category names are unique per group.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final GroupRepository groupRepository;
    private final AuditLogService auditLogService;

    /**
     * Returns a category by its id.
     *
     * @param id the category id
     * @return the category
     * @throws ResourceNotFoundException if no category has that id
     */
    public CategoryResponse getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.CATEGORY_NOT_FOUND));
        return CategoryResponse.from(category);
    }

    /**
     * Returns all active categories for a group.
     *
     * @param groupId the group id
     * @return the group's active categories
     */
    public List<CategoryResponse> getCategoriesByGroup(Long groupId) {
        return categoryRepository.findByGroupIdGroupAndIsActiveTrue(groupId).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    /**
     * Creates a category in a group.
     *
     * @param request the category details (name, color, icon, group)
     * @return the created category
     * @throws ResourceNotFoundException  if the group does not exist
     * @throws DuplicateResourceException if the name is already used in that group
     */
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        Group group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));

        if (categoryRepository.existsByGroupIdGroupAndName(request.getGroupId(), request.getName())) {
            throw new DuplicateResourceException("Category with this name already exists in the group");
        }

        Category category = Category.builder()
                .name(request.getName())
                .color(request.getColor())
                .icon(request.getIcon())
                .isActive(true)
                .group(group)
                .build();
        Category saved = categoryRepository.save(category);
        log.info("Category created: id={} group={}", saved.getIdCategory(), group.getIdGroup());
        auditLogService.recordEvent(AuditEntityType.CATEGORY, saved.getIdCategory(), AuditAction.CREATE,
                auditMessage(saved.getName(), "created"), group);
        return CategoryResponse.from(saved);
    }

    /**
     * Updates a category's name, color, and icon.
     *
     * @param id      the category id
     * @param request the new category details
     * @return the updated category
     * @throws ResourceNotFoundException if no category has that id
     */
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.CATEGORY_NOT_FOUND));
        category.setName(request.getName());
        category.setColor(request.getColor());
        category.setIcon(request.getIcon());
        Category saved = categoryRepository.save(category);
        auditLogService.recordEvent(AuditEntityType.CATEGORY, saved.getIdCategory(), AuditAction.UPDATE,
                auditMessage(saved.getName(), "updated"), saved.getGroup());
        return CategoryResponse.from(saved);
    }

    /**
     * Soft-deletes a category (sets it inactive).
     *
     * @param id the category id
     * @throws ResourceNotFoundException if no category has that id
     */
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.CATEGORY_NOT_FOUND));
        category.setIsActive(false);
        categoryRepository.save(category);
        log.info("Category soft-deleted: id={}", id);
        auditLogService.recordEvent(AuditEntityType.CATEGORY, category.getIdCategory(), AuditAction.DELETE,
                auditMessage(category.getName(), "deleted"), category.getGroup());
    }

    private static String auditMessage(String name, String verb) {
        return "Category \"" + name + "\" " + verb;
    }
}
