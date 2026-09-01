package com.tasklean.api.domain.category;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.category.dto.CategoryRequest;
import com.tasklean.api.domain.category.dto.CategoryResponse;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final GroupRepository groupRepository;

    public CategoryResponse getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.CATEGORY_NOT_FOUND));
        return CategoryResponse.from(category);
    }

    public List<CategoryResponse> getCategoriesByGroup(Long groupId) {
        return categoryRepository.findByGroupIdGroupAndIsActiveTrue(groupId).stream()
                .map(CategoryResponse::from)
                .toList();
    }

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
        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.CATEGORY_NOT_FOUND));
        category.setName(request.getName());
        category.setColor(request.getColor());
        category.setIcon(request.getIcon());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.CATEGORY_NOT_FOUND));
        category.setIsActive(false);
        categoryRepository.save(category);
        log.info("Category soft-deleted: id={}", id);
    }
}
