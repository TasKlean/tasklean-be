package com.tasklean.api.domain.category;

import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.category.dto.CategoryRequest;
import com.tasklean.api.domain.category.dto.CategoryResponse;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private AuditLogService auditLogService;
    @InjectMocks
    private CategoryService categoryService;

    private static final Long GROUP_ID = 10L;

    private Group group() {
        return Group.builder().idGroup(GROUP_ID).build();
    }

    private CategoryRequest request() {
        CategoryRequest r = new CategoryRequest();
        r.setName("Kitchen");
        r.setColor("#FFF");
        r.setIcon("kitchen");
        r.setGroupId(GROUP_ID);
        return r;
    }

    private Category category() {
        return Category.builder().idCategory(1L).name("Kitchen").isActive(true).group(group()).build();
    }

    @Test
    void getCategoryById_found_returns() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category()));
        assertThat(categoryService.getCategoryById(1L).getId()).isEqualTo(1L);
    }

    @Test
    void getCategoryById_missing_throws() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> categoryService.getCategoryById(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCategoriesByGroup_returnsList() {
        when(categoryRepository.findByGroupIdGroupAndIsActiveTrue(GROUP_ID)).thenReturn(List.of(category()));
        assertThat(categoryService.getCategoriesByGroup(GROUP_ID)).hasSize(1);
    }

    @Test
    void createCategory_success_savesAndAudits() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
        when(categoryRepository.existsByGroupIdGroupAndName(GROUP_ID, "Kitchen")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> {
            Category c = i.getArgument(0);
            c.setIdCategory(1L);
            return c;
        });

        CategoryResponse response = categoryService.createCategory(request());

        assertThat(response.getName()).isEqualTo("Kitchen");
        verify(auditLogService).recordEvent(eq(AuditEntityType.CATEGORY), eq(1L), eq(AuditAction.CREATE), any(), any(Group.class));
    }

    @Test
    void createCategory_groupMissing_throws() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> categoryService.createCategory(request())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createCategory_duplicateName_throws() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
        when(categoryRepository.existsByGroupIdGroupAndName(GROUP_ID, "Kitchen")).thenReturn(true);
        assertThatThrownBy(() -> categoryService.createCategory(request())).isInstanceOf(DuplicateResourceException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_success() {
        Category existing = category();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        categoryService.updateCategory(1L, request());

        assertThat(existing.getName()).isEqualTo("Kitchen");
        verify(auditLogService).recordEvent(eq(AuditEntityType.CATEGORY), eq(1L), eq(AuditAction.UPDATE), any(), any());
    }

    @Test
    void updateCategory_missing_throws() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> categoryService.updateCategory(1L, request())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteCategory_success_softDeletesAndAudits() {
        Category existing = category();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));

        categoryService.deleteCategory(1L);

        assertThat(existing.getIsActive()).isFalse();
        verify(auditLogService).recordEvent(eq(AuditEntityType.CATEGORY), eq(1L), eq(AuditAction.DELETE), any(), any());
    }

    @Test
    void deleteCategory_missing_throws() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> categoryService.deleteCategory(1L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
