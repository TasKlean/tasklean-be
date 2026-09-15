package com.tasklean.api.domain.tag;

import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.tag.dto.TagRequest;
import com.tasklean.api.domain.tag.dto.TagResponse;
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
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private AuditLogService auditLogService;
    @InjectMocks
    private TagService tagService;

    private static final Long GROUP_ID = 10L;

    private Group group() {
        return Group.builder().idGroup(GROUP_ID).build();
    }

    private TagRequest request() {
        TagRequest r = new TagRequest();
        r.setName("quick");
        r.setColor("#0F0");
        r.setGroupId(GROUP_ID);
        return r;
    }

    private Tag tag() {
        return Tag.builder().idTag(1L).name("quick").isActive(true).group(group()).build();
    }

    @Test
    void getTagById_found_returns() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag()));
        assertThat(tagService.getTagById(1L).getId()).isEqualTo(1L);
    }

    @Test
    void getTagById_missing_throws() {
        when(tagRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> tagService.getTagById(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTagsByGroup_returnsList() {
        when(tagRepository.findByGroupIdGroupAndIsActiveTrue(GROUP_ID)).thenReturn(List.of(tag()));
        assertThat(tagService.getTagsByGroup(GROUP_ID)).hasSize(1);
    }

    @Test
    void createTag_success_savesAndAudits() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
        when(tagRepository.existsByGroupIdGroupAndName(GROUP_ID, "quick")).thenReturn(false);
        when(tagRepository.save(any(Tag.class))).thenAnswer(i -> {
            Tag t = i.getArgument(0);
            t.setIdTag(1L);
            return t;
        });

        TagResponse response = tagService.createTag(request());

        assertThat(response.getName()).isEqualTo("quick");
        verify(auditLogService).recordEvent(eq(AuditEntityType.TAG), eq(1L), eq(AuditAction.CREATE), any(), any(Group.class));
    }

    @Test
    void createTag_groupMissing_throws() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> tagService.createTag(request())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createTag_duplicateName_throws() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
        when(tagRepository.existsByGroupIdGroupAndName(GROUP_ID, "quick")).thenReturn(true);
        assertThatThrownBy(() -> tagService.createTag(request())).isInstanceOf(DuplicateResourceException.class);
        verify(tagRepository, never()).save(any());
    }

    @Test
    void updateTag_success() {
        Tag existing = tag();
        when(tagRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(tagRepository.save(any(Tag.class))).thenAnswer(i -> i.getArgument(0));

        tagService.updateTag(1L, request());

        assertThat(existing.getName()).isEqualTo("quick");
        verify(auditLogService).recordEvent(eq(AuditEntityType.TAG), eq(1L), eq(AuditAction.UPDATE), any(), any());
    }

    @Test
    void updateTag_missing_throws() {
        when(tagRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> tagService.updateTag(1L, request())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteTag_success_softDeletesAndAudits() {
        Tag existing = tag();
        when(tagRepository.findById(1L)).thenReturn(Optional.of(existing));

        tagService.deleteTag(1L);

        assertThat(existing.getIsActive()).isFalse();
        verify(auditLogService).recordEvent(eq(AuditEntityType.TAG), eq(1L), eq(AuditAction.DELETE), any(), any());
    }

    @Test
    void deleteTag_missing_throws() {
        when(tagRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> tagService.deleteTag(1L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
