package com.tasklean.api.domain.task;

import com.tasklean.api.common.exception.InvalidRequestException;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.task.dto.TaskBoardFilterRequest;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * What a caller may ask of a board, and what each answer narrows to. The specification is applied
 * against a recording criteria builder, so a case asserts the shape the filter composed rather than
 * rows it would have returned.
 */
@ExtendWith(MockitoExtension.class)
class TaskBoardFilterTest {

    @Mock
    private GroupMemberRepository groupMemberRepository;

    // Fixed rather than the usual systemUTC spy: two of these cases assert the very instant the
    // filter compared against, which a clock that ticks between the call and the check cannot give.
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-11-20T09:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private TaskBoardFilter taskBoardFilter;

    @SuppressWarnings("unchecked")
    private final Root<Task> root = mock(Root.class, RETURNS_DEEP_STUBS);

    @SuppressWarnings("unchecked")
    private final CriteriaQuery<Object> query = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);

    private final CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

    private static final Long GROUP_ID = 10L;

    private TaskBoardFilterRequest asked() {
        return new TaskBoardFilterRequest();
    }

    private void apply(Specification<Task> spec) {
        spec.toPredicate(root, query, cb);
    }

    private GroupMember member(Long groupId) {
        return GroupMember.builder().idGroupMember(4L).uid("gm-4").isActive(true)
                .group(Group.builder().idGroup(groupId).build()).build();
    }

    @Test
    void narrow_nothingAsked_keepsTheChoresStillWaitingToBeDone() {
        apply(taskBoardFilter.narrow(GROUP_ID, asked()));

        // The board is a to-do list, so a completed or archived chore is something you ask for.
        verify(root.get("status")).in(TaskStatus.OPEN);
    }

    @Test
    void narrow_noStatusNamed_isTheSameAsNoneAsked() {
        TaskBoardFilterRequest r = asked();
        r.setStatus(List.of());

        apply(taskBoardFilter.narrow(GROUP_ID, r));

        verify(root.get("status")).in(TaskStatus.OPEN);
    }

    @Test
    void narrow_statusNamed_keepsThatStateInstead() {
        TaskBoardFilterRequest r = asked();
        r.setStatus(List.of(TaskStatus.COMPLETED));

        apply(taskBoardFilter.narrow(GROUP_ID, r));

        verify(root.get("status")).in(Set.of(TaskStatus.COMPLETED));
    }

    @Test
    void narrow_anAssignee_resolvesTheUidToTheirMembershipId() {
        TaskBoardFilterRequest r = asked();
        r.setAssignedToUid("gm-4");
        when(groupMemberRepository.findByUid("gm-4")).thenReturn(Optional.of(member(GROUP_ID)));

        apply(taskBoardFilter.narrow(GROUP_ID, r));

        verify(cb).equal(root.get("assignedTo").get("idGroupMember"), 4L);
    }

    @Test
    void narrow_anAssigneeFromAnotherHousehold_narrowsToAnEmptyBoard() {
        TaskBoardFilterRequest r = asked();
        r.setAssignedToUid("gm-4");
        when(groupMemberRepository.findByUid("gm-4")).thenReturn(Optional.of(member(77L)));

        apply(taskBoardFilter.narrow(GROUP_ID, r));

        // Empty rather than 404: a board is a filter, and a filter matching nothing is not a
        // missing resource. It also reads the same as a uid naming nobody at all.
        verify(cb).disjunction();
    }

    @Test
    void narrow_anAssigneeNamingNobody_narrowsToAnEmptyBoard() {
        TaskBoardFilterRequest r = asked();
        r.setAssignedToUid("gm-nobody");
        when(groupMemberRepository.findByUid("gm-nobody")).thenReturn(Optional.empty());

        apply(taskBoardFilter.narrow(GROUP_ID, r));

        verify(cb).disjunction();
    }

    @Test
    void narrow_theUnassignedPool_needsNoLookupAtAll() {
        TaskBoardFilterRequest r = asked();
        r.setUnassigned(true);

        apply(taskBoardFilter.narrow(GROUP_ID, r));

        verify(cb).isNull(root.get("assignedTo"));
        verifyNoInteractions(groupMemberRepository);
    }

    @Test
    void narrow_anAssigneeAndTheUnassignedPool_isRefused() {
        TaskBoardFilterRequest r = asked();
        r.setAssignedToUid("gm-4");
        r.setUnassigned(true);

        // Contradictory rather than empty: asking for one person's chores and for nobody's cannot
        // be what the caller meant, and an empty board would look like an answer.
        assertThatThrownBy(() -> taskBoardFilter.narrow(GROUP_ID, r))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void narrow_aBlankAssignee_countsAsNoneAsked() {
        TaskBoardFilterRequest r = asked();
        r.setAssignedToUid("   ");

        apply(taskBoardFilter.narrow(GROUP_ID, r));

        verifyNoInteractions(groupMemberRepository);
    }

    @Test
    void narrow_aTag_asksForTheTasksCarryingIt() {
        TaskBoardFilterRequest r = asked();
        r.setTagId(7L);

        apply(taskBoardFilter.narrow(GROUP_ID, r));

        verify(query).subquery(Long.class);
    }

    @Test
    void narrow_noPriorityNamed_addsNoPriorityPredicate() {
        TaskBoardFilterRequest r = asked();
        r.setPriority(List.of());

        apply(taskBoardFilter.narrow(GROUP_ID, r));

        verify(root.get("priority"), never()).in(any(java.util.Collection.class));
    }

    @Test
    void narrow_overdueNotMentioned_addsNoDueDatePredicate() {
        apply(taskBoardFilter.narrow(GROUP_ID, asked()));

        // An absent filter must contribute no predicate rather than a comparison against now.
        verify(cb, never()).lessThan(root.get("nextDueDate"), LocalDateTime.now(clock));
    }

    @Test
    void narrow_overdueAskedFor_comparesAgainstTheApplicationClock() {
        TaskBoardFilterRequest r = asked();
        r.setOverdue(true);

        apply(taskBoardFilter.narrow(GROUP_ID, r));

        verify(cb).lessThan(root.get("nextDueDate"), LocalDateTime.now(clock));
    }

    @Test
    void narrow_aBlankSearch_countsAsNoneAsked() {
        TaskBoardFilterRequest r = asked();
        r.setQ("   ");

        apply(taskBoardFilter.narrow(GROUP_ID, r));

        verify(cb, never()).like(any(), any(String.class));
    }

    @Test
    void pageFor_nothingAsked_isTheFirstPageAtTheDefaultSize() {
        Pageable page = taskBoardFilter.pageFor(asked());

        assertThat(page.getPageNumber()).isZero();
        assertThat(page.getPageSize()).isEqualTo(20);
    }

    @Test
    void pageFor_emptyParameters_fallBackToTheDefaults() {
        TaskBoardFilterRequest r = asked();
        r.setPage(null);
        r.setSize(null);

        // "page=" with nothing after it binds to null rather than to the field's initializer.
        Pageable page = taskBoardFilter.pageFor(r);

        assertThat(page.getPageNumber()).isZero();
        assertThat(page.getPageSize()).isEqualTo(20);
    }

    @Test
    void pageFor_aPageAsked_carriesNoSortOfItsOwn() {
        TaskBoardFilterRequest r = asked();
        r.setPage(3);
        r.setSize(50);

        Pageable page = taskBoardFilter.pageFor(r);

        assertThat(page.getPageNumber()).isEqualTo(3);
        assertThat(page.getPageSize()).isEqualTo(50);
        // The ordering lives in the specification: one of the orderings cannot be named as a
        // property, and splitting them across both would leave two places to look.
        assertThat(page.getSort().isSorted()).isFalse();
    }
}
