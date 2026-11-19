package com.tasklean.api.security;

import com.tasklean.api.domain.category.Category;
import com.tasklean.api.domain.category.CategoryRepository;
import com.tasklean.api.domain.group.Group;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategorySecurityTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private GroupSecurity groupSecurity;

    @InjectMocks
    private CategorySecurity categorySecurity;

    private static final Long GROUP_ID = 10L;

    private void categoryExists() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(Category.builder()
                .idCategory(1L).group(Group.builder().idGroup(GROUP_ID).build()).build()));
    }

    @Test
    void canView_memberOfTheHousehold_isTrue() {
        categoryExists();
        when(groupSecurity.isMemberOfGroup(GROUP_ID)).thenReturn(true);

        assertThat(categorySecurity.canView(1L)).isTrue();
    }

    @Test
    void canView_outsider_isFalse() {
        categoryExists();
        when(groupSecurity.isMemberOfGroup(GROUP_ID)).thenReturn(false);

        assertThat(categorySecurity.canView(1L)).isFalse();
    }

    @Test
    void canManage_plainMember_isFalse() {
        // A category is the household's filing system, not one member's.
        categoryExists();
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(false);

        assertThat(categorySecurity.canManage(1L)).isFalse();
    }

    @Test
    void canManage_householdAdmin_isTrue() {
        categoryExists();
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(true);

        assertThat(categorySecurity.canManage(1L)).isTrue();
    }

    @Test
    void canView_noSuchCategory_isFalse() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(categorySecurity.canView(1L)).isFalse();
        verifyNoInteractions(groupSecurity);
    }

    @Test
    void canView_deletedCategory_stillResolves() {
        // Deliberately unfiltered: the member reaches the service and gets a 404, while an
        // outsider is still stopped here with a 403. Filtering would collapse the two.
        Category deleted = Category.builder().idCategory(1L).isActive(false)
                .group(Group.builder().idGroup(GROUP_ID).build()).build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(deleted));
        when(groupSecurity.isMemberOfGroup(GROUP_ID)).thenReturn(true);

        assertThat(categorySecurity.canView(1L)).isTrue();
    }

    @Test
    void canView_noId_isFalse() {
        assertThat(categorySecurity.canView(null)).isFalse();
        verifyNoInteractions(categoryRepository, groupSecurity);
    }
}
