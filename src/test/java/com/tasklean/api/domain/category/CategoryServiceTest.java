package com.tasklean.api.domain.category;

import com.tasklean.api.common.exception.BusinessRuleException;
import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.category.dto.CategoryCreateRequest;
import com.tasklean.api.domain.category.dto.CategoryUpdateRequest;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.task.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private TaskRepository taskRepository;
    @Mock
    private AuditLogService auditLogService;
    @InjectMocks
    private CategoryService categoryService;

    private static final Long GROUP_ID = 10L;

    private Group group() {
        return Group.builder().idGroup(GROUP_ID).uid("grp-1").isActive(true).build();
    }

    private Category category(Long id, String name) {
        return Category.builder().idCategory(id).name(name).isActive(true).group(group()).build();
    }

    private CategoryCreateRequest createRequest(String name) {
        CategoryCreateRequest r = new CategoryCreateRequest();
        r.setGroupUid("grp-1");
        r.setName(name);
        r.setColor("#FF6B35");
        return r;
    }

    private CategoryUpdateRequest updateRequest(String name) {
        CategoryUpdateRequest r = new CategoryUpdateRequest();
        r.setName(name);
        return r;
    }

    private void groupExists() {
        when(groupRepository.findByUidAndIsActiveTrue("grp-1")).thenReturn(Optional.of(group()));
    }

    private void savesWhatItIsGiven() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Category saved() {
        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void getCategoryById_found_returnsIt() {
        when(categoryRepository.findByIdCategoryAndIsActiveTrue(1L))
                .thenReturn(Optional.of(category(1L, "Kitchen")));

        assertThat(categoryService.getCategoryById(1L).getName()).isEqualTo("Kitchen");
    }

    @Test
    void getCategoryById_deleted_isNotFound() {
        when(categoryRepository.findByIdCategoryAndIsActiveTrue(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryById(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCategoriesByGroup_deletedHousehold_isNotFound() {
        when(groupRepository.findByUidAndIsActiveTrue("grp-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoriesByGroup("grp-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCategoriesByGroup_returnsThem() {
        groupExists();
        when(categoryRepository.findByGroupIdGroupAndIsActiveTrueOrderByName(GROUP_ID))
                .thenReturn(List.of(category(1L, "Bathroom"), category(2L, "Kitchen")));

        assertThat(categoryService.getCategoriesByGroup("grp-1"))
                .extracting("name").containsExactly("Bathroom", "Kitchen");
    }

    @Test
    void createCategory_newName_insertsIt() {
        groupExists();
        when(categoryRepository.findByGroupAndName(GROUP_ID, "Kitchen")).thenReturn(Optional.empty());
        savesWhatItIsGiven();

        categoryService.createCategory(createRequest("Kitchen"));

        assertThat(saved().getIdCategory()).isNull();
        assertThat(saved().getIsActive()).isTrue();
    }

    @Test
    void createCategory_nameNormalised_beforeItIsStored() {
        groupExists();
        when(categoryRepository.findByGroupAndName(GROUP_ID, "Common Areas")).thenReturn(Optional.empty());
        savesWhatItIsGiven();

        categoryService.createCategory(createRequest("  Common   Areas "));

        // Normalised on the way in, so the name the uniqueness check asked about is the one stored.
        assertThat(saved().getName()).isEqualTo("Common Areas");
    }

    @Test
    void createCategory_nameInUse_isRefused() {
        groupExists();
        when(categoryRepository.findByGroupAndName(GROUP_ID, "Kitchen"))
                .thenReturn(Optional.of(category(1L, "Kitchen")));

        assertThatThrownBy(() -> categoryService.createCategory(createRequest("Kitchen")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void createCategory_nameHeldByADeletedOne_revivesThatRow() {
        groupExists();
        Category deleted = category(1L, "Kitchen");
        deleted.setIsActive(false);
        when(categoryRepository.findByGroupAndName(GROUP_ID, "Kitchen")).thenReturn(Optional.of(deleted));
        savesWhatItIsGiven();

        categoryService.createCategory(createRequest("Kitchen"));

        // The unique index covers deleted rows, so a second "Kitchen" could not be inserted - and
        // reviving this one gives every task still filed under it its classification back.
        assertThat(saved().getIdCategory()).isEqualTo(1L);
        assertThat(saved().getIsActive()).isTrue();
        assertThat(saved().getColor()).isEqualTo("#FF6B35");
    }

    @Test
    void createCategory_deletedHousehold_isNotFound() {
        when(groupRepository.findByUidAndIsActiveTrue("grp-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.createCategory(createRequest("Kitchen")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateCategory_newName_appliesIt() {
        when(categoryRepository.findByIdCategoryAndIsActiveTrue(1L))
                .thenReturn(Optional.of(category(1L, "Kitchen")));
        when(categoryRepository.findByGroupAndName(GROUP_ID, "Scullery")).thenReturn(Optional.empty());
        savesWhatItIsGiven();

        categoryService.updateCategory(1L, updateRequest("Scullery"));

        assertThat(saved().getName()).isEqualTo("Scullery");
    }

    @Test
    void updateCategory_keepingItsOwnName_isAllowed() {
        // The collision check has to exclude the row being edited, or saving a colour change
        // alone would report the category as a duplicate of itself.
        when(categoryRepository.findByIdCategoryAndIsActiveTrue(1L))
                .thenReturn(Optional.of(category(1L, "Kitchen")));
        when(categoryRepository.findByGroupAndName(GROUP_ID, "Kitchen"))
                .thenReturn(Optional.of(category(1L, "Kitchen")));
        savesWhatItIsGiven();

        assertThat(categoryService.updateCategory(1L, updateRequest("Kitchen")).getName())
                .isEqualTo("Kitchen");
    }

    @Test
    void updateCategory_nameOfAnother_isRefused() {
        when(categoryRepository.findByIdCategoryAndIsActiveTrue(1L))
                .thenReturn(Optional.of(category(1L, "Kitchen")));
        when(categoryRepository.findByGroupAndName(GROUP_ID, "Bathroom"))
                .thenReturn(Optional.of(category(2L, "Bathroom")));

        assertThatThrownBy(() -> categoryService.updateCategory(1L, updateRequest("Bathroom")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_nameOfADeletedOne_isRefused() {
        // Refused rather than left to the database: the index covers deleted rows, so this would
        // otherwise surface as a constraint violation instead of a 409.
        Category deleted = category(2L, "Bathroom");
        deleted.setIsActive(false);
        when(categoryRepository.findByIdCategoryAndIsActiveTrue(1L))
                .thenReturn(Optional.of(category(1L, "Kitchen")));
        when(categoryRepository.findByGroupAndName(GROUP_ID, "Bathroom")).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> categoryService.updateCategory(1L, updateRequest("Bathroom")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void updateCategory_deleted_isNotFound() {
        when(categoryRepository.findByIdCategoryAndIsActiveTrue(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(1L, updateRequest("Scullery")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteCategory_unused_marksItInactive() {
        when(categoryRepository.findByIdCategoryAndIsActiveTrue(1L))
                .thenReturn(Optional.of(category(1L, "Kitchen")));
        when(taskRepository.countActiveByCategory(1L)).thenReturn(0L);

        categoryService.deleteCategory(1L);

        assertThat(saved().getIsActive()).isFalse();
        verify(auditLogService).recordEvent(eq(AuditEntityType.CATEGORY), eq(1L),
                eq(AuditAction.DELETE), any(), any());
    }

    @Test
    void deleteCategory_stillUsedByTasks_isRefused() {
        when(categoryRepository.findByIdCategoryAndIsActiveTrue(1L))
                .thenReturn(Optional.of(category(1L, "Kitchen")));
        when(taskRepository.countActiveByCategory(1L)).thenReturn(3L);

        // Refused rather than clearing the category off three chores as a side effect.
        assertThatThrownBy(() -> categoryService.deleteCategory(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("3");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void deleteCategory_alreadyDeleted_isNotFound() {
        when(categoryRepository.findByIdCategoryAndIsActiveTrue(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deleteCategory(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
