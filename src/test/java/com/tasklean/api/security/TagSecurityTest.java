package com.tasklean.api.security;

import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.tag.Tag;
import com.tasklean.api.domain.tag.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagSecurityTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private GroupSecurity groupSecurity;

    @InjectMocks
    private TagSecurity tagSecurity;

    private static final Long GROUP_ID = 10L;

    private void tagExists() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(Tag.builder()
                .idTag(1L).group(Group.builder().idGroup(GROUP_ID).build()).build()));
    }

    @Test
    void canView_memberOfTheHousehold_isTrue() {
        tagExists();
        when(groupSecurity.isMemberOfGroup(GROUP_ID)).thenReturn(true);

        assertThat(tagSecurity.canView(1L)).isTrue();
    }

    @Test
    void canView_outsider_isFalse() {
        tagExists();
        when(groupSecurity.isMemberOfGroup(GROUP_ID)).thenReturn(false);

        assertThat(tagSecurity.canView(1L)).isFalse();
    }

    @Test
    void canManage_plainMember_isFalse() {
        // Any member may add a tag; renaming or removing one changes what everybody else sees on
        // their own tasks, so that much needs an admin.
        tagExists();
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(false);

        assertThat(tagSecurity.canManage(1L)).isFalse();
    }

    @Test
    void canManage_householdAdmin_isTrue() {
        tagExists();
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(true);

        assertThat(tagSecurity.canManage(1L)).isTrue();
    }

    @Test
    void canView_noSuchTag_isFalse() {
        when(tagRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(tagSecurity.canView(1L)).isFalse();
        verifyNoInteractions(groupSecurity);
    }

    @Test
    void canView_deletedTag_stillResolves() {
        Tag deleted = Tag.builder().idTag(1L).isActive(false)
                .group(Group.builder().idGroup(GROUP_ID).build()).build();
        when(tagRepository.findById(1L)).thenReturn(Optional.of(deleted));
        when(groupSecurity.isMemberOfGroup(GROUP_ID)).thenReturn(true);

        assertThat(tagSecurity.canView(1L)).isTrue();
    }

    @Test
    void canView_noId_isFalse() {
        assertThat(tagSecurity.canView(null)).isFalse();
        verifyNoInteractions(tagRepository, groupSecurity);
    }
}
