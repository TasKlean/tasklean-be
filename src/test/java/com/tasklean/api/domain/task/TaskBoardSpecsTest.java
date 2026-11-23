package com.tasklean.api.domain.task;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The predicates a board is composed from. Built against a recording criteria builder rather than a
 * database: what matters here is the shape each specification asks for, and two of them depend on
 * which query they are running against, which no live request shows you in isolation.
 */
class TaskBoardSpecsTest {

    @SuppressWarnings("unchecked")
    private final Root<Task> root = mock(Root.class, RETURNS_DEEP_STUBS);

    @SuppressWarnings("unchecked")
    private final CriteriaQuery<Object> query = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);

    private final CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void asCountQuery() {
        when(query.getResultType()).thenReturn((Class) Long.class);
    }

    @Test
    void liveInGroup_always_narrowsToThatHouseholdAndSkipsDeletedRows() {
        TaskBoardSpecs.liveInGroup(10L).toPredicate(root, query, cb);

        verify(cb).equal(root.get("group").get("idGroup"), 10L);
        verify(cb).isTrue(root.get("isActive"));
    }

    @Test
    void carryingTag_always_asksWithASubqueryRatherThanAJoin() {
        // A join returns a row per matching join row, which multiplies a task across the page and
        // corrupts the count the page numbers are derived from.
        TaskBoardSpecs.carryingTag(7L).toPredicate(root, query, cb);

        verify(query).subquery(Long.class);
        verify(cb).exists(any());
        verify(root, never()).join(anyString());
    }

    @Test
    void carryingTag_always_ignoresADeletedTag() {
        TaskBoardSpecs.carryingTag(7L).toPredicate(root, query, cb);

        verify(cb).isTrue(any());
    }

    @Test
    void unassigned_always_matchesTheHouseholdPool() {
        TaskBoardSpecs.unassigned().toPredicate(root, query, cb);

        verify(cb).isNull(root.get("assignedTo"));
    }

    @Test
    void overdue_asked_matchesOnlyChoresStillWaitingToBeDone() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        TaskBoardSpecs.overdue(now, true).toPredicate(root, query, cb);

        // The status half is the rule: without it a chore completed late stays on the overdue
        // board for good, and an archived one never leaves it.
        verify(cb).lessThan(root.get("nextDueDate"), now);
        verify(root.get("status")).in(TaskStatus.OPEN);
        verify(cb, never()).not(any());
    }

    @Test
    void overdue_askedForTheOnesThatAreNot_spellsOutTheUndatedCase() {
        TaskBoardSpecs.overdue(LocalDateTime.now(ZoneOffset.UTC), false).toPredicate(root, query, cb);

        // The negation alone is not enough: comparing a null due date yields unknown, and NOT
        // unknown is still unknown, so an undated chore falls out of both sides of its own filter.
        verify(cb).not(any());
        verify(cb).isNull(root.get("nextDueDate"));
        verify(cb).or(any(), any());
    }

    @Test
    void nameContains_always_matchesCaseInsensitivelyAnywhere() {
        TaskBoardSpecs.nameContains("Dishes").toPredicate(root, query, cb);

        verify(cb).lower(root.get("name"));
        verify(cb).like(cb.lower(root.get("name")), "%dishes%");
    }

    @Test
    void withStatusIn_always_keepsEveryStateNamed() {
        TaskBoardSpecs.withStatusIn(Set.of(TaskStatus.COMPLETED)).toPredicate(root, query, cb);

        verify(root.get("status")).in(Set.of(TaskStatus.COMPLETED));
    }

    @Test
    void withPeopleFetched_contentQuery_fetchesTheHouseholdAndBothMemberships() {
        TaskBoardSpecs.withPeopleFetched().toPredicate(root, query, cb);

        verify(root).fetch("group", JoinType.LEFT);
        verify(root).fetch("createdBy", JoinType.LEFT);
        verify(root).fetch("assignedTo", JoinType.LEFT);
    }

    @Test
    void withPeopleFetched_countQuery_fetchesNothing() {
        // A fetch join on the count query leaves the fetched association with no owner in the
        // selection, and Hibernate rejects the statement outright.
        asCountQuery();

        TaskBoardSpecs.withPeopleFetched().toPredicate(root, query, cb);

        verify(root, never()).fetch(anyString(), any(JoinType.class));
    }

    @Test
    void ordered_always_endsOnTheRowId() {
        TaskBoardSpecs.ordered(TaskSort.DUE_ASC).toPredicate(root, query, cb);

        // Without a unique last key two tasks that tie can come back in either order, and under
        // page numbers that means one of them on both pages and the other on neither.
        verify(cb).asc(root.get("idTask"));
        verify(query).orderBy(anyList());
    }

    @Test
    void ordered_byDueDate_sortsUndatedChoresLastRatherThanFirst() {
        TaskBoardSpecs.ordered(TaskSort.DUE_DESC).toPredicate(root, query, cb);

        // Postgres puts nulls first on DESC, so an undated chore would lead a "latest due" board.
        verify(cb).isNull(root.get("nextDueDate"));
        verify(cb).desc(root.get("nextDueDate"));
    }

    @Test
    void ordered_byPriority_ranksTheEnumRatherThanTheStoredString() {
        TaskBoardSpecs.ordered(TaskSort.PRIORITY_HIGH_FIRST).toPredicate(root, query, cb);

        // The column holds the name, so ordering it alphabetically gives HIGH, LOW, MEDIUM.
        verify(cb).selectCase(root.get("priority"));
    }

    @Test
    void ordered_byPriority_sortsAnUnsetPriorityAfterEveryNamedOne() {
        CriteriaBuilder.SimpleCase<TaskPriority, Integer> start = cb.selectCase(root.get("priority"));
        CriteriaBuilder.SimpleCase<TaskPriority, Integer> afterMedium =
                start.when(TaskPriority.HIGH, 0).when(TaskPriority.MEDIUM, 1);

        TaskBoardSpecs.ordered(TaskSort.PRIORITY_HIGH_FIRST).toPredicate(root, query, cb);

        // LOW needs a branch of its own: left to otherwise(), an unset priority would rank with
        // it, a CASE comparing null matching no branch at all.
        verify(afterMedium).when(TaskPriority.LOW, 2);
    }

    @Test
    void ordered_noSortAsked_fallsBackToSoonestDue() {
        TaskBoardSpecs.ordered(null).toPredicate(root, query, cb);

        verify(cb).asc(root.get("nextDueDate"));
    }

    @Test
    void ordered_countQuery_ordersNothing() {
        asCountQuery();

        TaskBoardSpecs.ordered(TaskSort.DUE_ASC).toPredicate(root, query, cb);

        verify(query, never()).orderBy(anyList());
        verify(query, never()).orderBy(List.of());
    }
}
