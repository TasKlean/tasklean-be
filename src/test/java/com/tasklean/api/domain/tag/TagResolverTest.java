package com.tasklean.api.domain.tag;

import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.group.Group;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagResolverTest {

    @Mock
    private TagRepository tagRepository;
    @Mock
    private AuditLogService auditLogService;
    @InjectMocks
    private TagResolver tagResolver;

    private static final Long GROUP_ID = 10L;

    private Group group() {
        return Group.builder().idGroup(GROUP_ID).uid("grp-1").isActive(true).build();
    }

    private Tag tag(Long id, String name, boolean active) {
        return Tag.builder().idTag(id).name(name).isActive(active).group(group()).build();
    }

    private void savesWhatItIsGiven() {
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> {
            Tag tag = invocation.getArgument(0);
            if (tag.getIdTag() == null) {
                tag.setIdTag(42L);
            }
            return tag;
        });
    }

    private Tag savedTag() {
        ArgumentCaptor<Tag> saved = ArgumentCaptor.forClass(Tag.class);
        verify(tagRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void resolve_noNames_returnsNothing() {
        assertThat(tagResolver.resolve(group(), null)).isEmpty();
        assertThat(tagResolver.resolve(group(), List.of())).isEmpty();
        verifyNoInteractions(tagRepository, auditLogService);
    }

    @Test
    void resolve_nameTheHouseholdAlreadyHas_reusesThatTag() {
        when(tagRepository.findByGroupAndName(GROUP_ID, "Kitchen"))
                .thenReturn(Optional.of(tag(7L, "Kitchen", true)));

        assertThat(tagResolver.resolve(group(), List.of("Kitchen")))
                .extracting(Tag::getIdTag).containsExactly(7L);
        verify(tagRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void resolve_differentCase_keepsTheHouseholdsOwnSpelling() {
        // Typing a label onto a task must not rename the tag everybody else sees on theirs.
        when(tagRepository.findByGroupAndName(GROUP_ID, "kitchen"))
                .thenReturn(Optional.of(tag(7L, "Kitchen", true)));

        assertThat(tagResolver.resolve(group(), List.of("kitchen")))
                .extracting(Tag::getName).containsExactly("Kitchen");
    }

    @Test
    void resolve_newName_createsTheTagInTheHousehold() {
        when(tagRepository.findByGroupAndName(GROUP_ID, "Kitchen")).thenReturn(Optional.empty());
        savesWhatItIsGiven();

        tagResolver.resolve(group(), List.of("Kitchen"));

        assertThat(savedTag().getName()).isEqualTo("Kitchen");
        assertThat(savedTag().getIsActive()).isTrue();
        assertThat(savedTag().getGroup().getIdGroup()).isEqualTo(GROUP_ID);
        verify(auditLogService).recordEvent(eq(AuditEntityType.TAG), eq(42L),
                eq(AuditAction.CREATE), eq("Tag \"Kitchen\" created"), any(Group.class));
    }

    @Test
    void resolve_untidyName_isNormalisedBeforeItIsMatched() {
        when(tagRepository.findByGroupAndName(GROUP_ID, "Deep Clean")).thenReturn(Optional.empty());
        savesWhatItIsGiven();

        tagResolver.resolve(group(), List.of("  Deep   Clean "));

        assertThat(savedTag().getName()).isEqualTo("Deep Clean");
    }

    @Test
    void resolve_theSameNameTwice_resolvesItOnce() {
        // Two spellings of one label in a single request would break the unique constraint on
        // task_tag, so they have to collapse before anything is written.
        when(tagRepository.findByGroupAndName(GROUP_ID, "Kitchen"))
                .thenReturn(Optional.of(tag(7L, "Kitchen", true)));

        assertThat(tagResolver.resolve(group(), List.of("Kitchen", "kitchen", " KITCHEN ")))
                .extracting(Tag::getIdTag).containsExactly(7L);
        verify(tagRepository, times(1)).findByGroupAndName(any(), any());
    }

    @Test
    void resolve_nameOfADeletedTag_revivesTheOriginalRow() {
        // The deleted tag keeps its task_tag rows, so the label comes back onto everything it was
        // already on - a new row by the same name would leave all of that behind.
        when(tagRepository.findByGroupAndName(GROUP_ID, "Kitchen"))
                .thenReturn(Optional.of(tag(7L, "Kitchen", false)));
        savesWhatItIsGiven();

        tagResolver.resolve(group(), List.of("Kitchen"));

        assertThat(savedTag().getIdTag()).isEqualTo(7L);
        assertThat(savedTag().getIsActive()).isTrue();
        verify(auditLogService).recordEvent(eq(AuditEntityType.TAG), eq(7L), eq(AuditAction.CREATE),
                eq("Tag \"Kitchen\" restored"), any(Group.class));
    }

    @Test
    void resolve_severalNames_returnsThemOrderedByName() {
        when(tagRepository.findByGroupAndName(GROUP_ID, "Zebra"))
                .thenReturn(Optional.of(tag(8L, "Zebra", true)));
        when(tagRepository.findByGroupAndName(GROUP_ID, "apple"))
                .thenReturn(Optional.of(tag(9L, "apple", true)));

        assertThat(tagResolver.resolve(group(), List.of("Zebra", "apple")))
                .extracting(Tag::getName).containsExactly("apple", "Zebra");
    }
}
