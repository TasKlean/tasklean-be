package com.tasklean.api.domain.tag;

import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.tag.dto.TagCreateRequest;
import com.tasklean.api.domain.tag.dto.TagQueryRequest;
import com.tasklean.api.domain.tag.dto.TagUpdateRequest;
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
        return Group.builder().idGroup(GROUP_ID).uid("grp-1").isActive(true).build();
    }

    private Tag tag(Long id, String name) {
        return Tag.builder().idTag(id).name(name).isActive(true).group(group()).build();
    }

    private TagCreateRequest createRequest(String name) {
        TagCreateRequest r = new TagCreateRequest();
        r.setGroupUid("grp-1");
        r.setName(name);
        r.setColor("#2ECC71");
        return r;
    }

    private TagUpdateRequest updateRequest(String name) {
        TagUpdateRequest r = new TagUpdateRequest();
        r.setName(name);
        return r;
    }

    private TagQueryRequest query(String q) {
        TagQueryRequest r = new TagQueryRequest();
        r.setGroupUid("grp-1");
        r.setQ(q);
        return r;
    }

    private void groupExists() {
        when(groupRepository.findByUidAndIsActiveTrue("grp-1")).thenReturn(Optional.of(group()));
    }

    private void savesWhatItIsGiven() {
        when(tagRepository.save(any(Tag.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Tag saved() {
        ArgumentCaptor<Tag> saved = ArgumentCaptor.forClass(Tag.class);
        verify(tagRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void getTagById_found_returnsIt() {
        when(tagRepository.findByIdTagAndIsActiveTrue(1L)).thenReturn(Optional.of(tag(1L, "quick")));

        assertThat(tagService.getTagById(1L).getName()).isEqualTo("quick");
    }

    @Test
    void getTagById_deleted_isNotFound() {
        when(tagRepository.findByIdTagAndIsActiveTrue(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.getTagById(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchTags_noSearch_asksForTheWholeList() {
        groupExists();
        when(tagRepository.searchActive(GROUP_ID, "")).thenReturn(List.of(tag(1L, "quick")));

        // An absent search becomes an empty string, which the LIKE treats as "everything" - so
        // there is one query for both cases rather than a branch.
        assertThat(tagService.searchTags(query(null))).hasSize(1);
    }

    @Test
    void searchTags_searchTrimmed_beforeItIsMatched() {
        groupExists();
        when(tagRepository.searchActive(GROUP_ID, "quick")).thenReturn(List.of(tag(1L, "quick")));

        assertThat(tagService.searchTags(query("  quick "))).hasSize(1);
    }

    @Test
    void searchTags_deletedHousehold_isNotFound() {
        when(groupRepository.findByUidAndIsActiveTrue("grp-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.searchTags(query(null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createTag_newName_insertsIt() {
        groupExists();
        when(tagRepository.findByGroupAndName(GROUP_ID, "quick")).thenReturn(Optional.empty());
        savesWhatItIsGiven();

        tagService.createTag(createRequest("quick"));

        assertThat(saved().getIdTag()).isNull();
        assertThat(saved().getIsActive()).isTrue();
    }

    @Test
    void createTag_nameNormalised_beforeItIsStored() {
        groupExists();
        when(tagRepository.findByGroupAndName(GROUP_ID, "deep clean")).thenReturn(Optional.empty());
        savesWhatItIsGiven();

        tagService.createTag(createRequest(" deep   clean "));

        assertThat(saved().getName()).isEqualTo("deep clean");
    }

    @Test
    void createTag_nameInUse_isRefused() {
        groupExists();
        when(tagRepository.findByGroupAndName(GROUP_ID, "quick")).thenReturn(Optional.of(tag(1L, "quick")));

        assertThatThrownBy(() -> tagService.createTag(createRequest("quick")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void createTag_nameHeldByADeletedOne_revivesThatRow() {
        groupExists();
        Tag deleted = tag(1L, "quick");
        deleted.setIsActive(false);
        when(tagRepository.findByGroupAndName(GROUP_ID, "quick")).thenReturn(Optional.of(deleted));
        savesWhatItIsGiven();

        tagService.createTag(createRequest("quick"));

        // The same label coming back, with whatever it was already attached to.
        assertThat(saved().getIdTag()).isEqualTo(1L);
        assertThat(saved().getIsActive()).isTrue();
    }

    @Test
    void updateTag_keepingItsOwnName_isAllowed() {
        when(tagRepository.findByIdTagAndIsActiveTrue(1L)).thenReturn(Optional.of(tag(1L, "quick")));
        when(tagRepository.findByGroupAndName(GROUP_ID, "quick")).thenReturn(Optional.of(tag(1L, "quick")));
        savesWhatItIsGiven();

        assertThat(tagService.updateTag(1L, updateRequest("quick")).getName()).isEqualTo("quick");
    }

    @Test
    void updateTag_nameOfAnother_isRefused() {
        when(tagRepository.findByIdTagAndIsActiveTrue(1L)).thenReturn(Optional.of(tag(1L, "quick")));
        when(tagRepository.findByGroupAndName(GROUP_ID, "weekly")).thenReturn(Optional.of(tag(2L, "weekly")));

        assertThatThrownBy(() -> tagService.updateTag(1L, updateRequest("weekly")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(tagRepository, never()).save(any());
    }

    @Test
    void updateTag_deleted_isNotFound() {
        when(tagRepository.findByIdTagAndIsActiveTrue(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.updateTag(1L, updateRequest("weekly")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteTag_marksItInactive() {
        when(tagRepository.findByIdTagAndIsActiveTrue(1L)).thenReturn(Optional.of(tag(1L, "quick")));

        tagService.deleteTag(1L);

        // No usage check, unlike a category: a tag is a loose label, so the tasks carrying it just
        // stop showing it.
        assertThat(saved().getIsActive()).isFalse();
        verify(auditLogService).recordEvent(eq(AuditEntityType.TAG), eq(1L),
                eq(AuditAction.DELETE), any(), any());
    }

    @Test
    void deleteTag_alreadyDeleted_isNotFound() {
        when(tagRepository.findByIdTagAndIsActiveTrue(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.deleteTag(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
