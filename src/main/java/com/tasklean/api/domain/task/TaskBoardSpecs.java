package com.tasklean.api.domain.task;

import com.tasklean.api.domain.tag.TaskTag;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * The predicates a task board is composed from. Each narrowing is its own specification so a new
 * board filter is a method here rather than another parameter threaded through the service and a
 * query per combination — the same reason the audit feed reads this way.
 */
final class TaskBoardSpecs {

    private static final String ASSIGNED_TO = "assignedTo";
    private static final String CATEGORY = "category";
    private static final String CREATED_BY = "createdBy";
    private static final String DEFAULT_ASSIGNEE = "defaultAssignee";
    private static final String DATE_CREATED = "dateCreated";
    private static final String GROUP = "group";
    private static final String ID_CATEGORY = "idCategory";
    private static final String ID_GROUP = "idGroup";
    private static final String ID_GROUP_MEMBER = "idGroupMember";
    private static final String ID_TAG = "idTag";
    private static final String ID_TASK = "idTask";
    private static final String IS_ACTIVE = "isActive";
    private static final String NAME = "name";
    private static final String NEXT_DUE_DATE = "nextDueDate";
    private static final String PRIORITY = "priority";
    private static final String STATUS = "status";
    private static final String TAG = "tag";
    private static final String TASK = "task";
    private static final String USER = "user";

    private TaskBoardSpecs() {
    }

    /**
     * The board's scope: one household's tasks that have not been deleted.
     *
     * @param groupId the group id
     * @return the specification
     */
    public static Specification<Task> liveInGroup(Long groupId) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get(GROUP).get(ID_GROUP), groupId),
                cb.isTrue(root.get(IS_ACTIVE)));
    }

    /**
     * Keeps the tasks in any of the given states.
     *
     * @param statuses the states to keep, never empty
     * @return the specification
     */
    public static Specification<Task> withStatusIn(Collection<TaskStatus> statuses) {
        return (root, query, cb) -> root.get(STATUS).in(statuses);
    }

    /**
     * Keeps the tasks assigned to one membership.
     *
     * @param membershipId the membership's internal id
     * @return the specification
     */
    public static Specification<Task> assignedTo(Long membershipId) {
        return (root, query, cb) -> cb.equal(root.get(ASSIGNED_TO).get(ID_GROUP_MEMBER), membershipId);
    }

    /**
     * Keeps the tasks nobody has taken.
     *
     * @return the specification
     */
    public static Specification<Task> unassigned() {
        return (root, query, cb) -> cb.isNull(root.get(ASSIGNED_TO));
    }

    /**
     * Keeps the tasks filed under one category.
     *
     * @param categoryId the category id
     * @return the specification
     */
    public static Specification<Task> inCategory(Long categoryId) {
        return (root, query, cb) -> cb.equal(root.get(CATEGORY).get(ID_CATEGORY), categoryId);
    }

    /**
     * Keeps the tasks carrying one tag, matched through a correlated subquery rather than a join.
     * A join would return a row per matching join row, which multiplies a task across the page and
     * corrupts both the count and the paging.
     *
     * @param tagId the tag id
     * @return the specification
     */
    public static Specification<Task> carryingTag(Long tagId) {
        return (root, query, cb) -> {
            Subquery<Long> carried = query.subquery(Long.class);
            Root<TaskTag> link = carried.from(TaskTag.class);
            carried.select(cb.literal(1L)).where(
                    cb.equal(link.get(TASK).get(ID_TASK), root.get(ID_TASK)),
                    cb.equal(link.get(TAG).get(ID_TAG), tagId),
                    // A deleted tag stops matching, the same way it stops being listed on a task.
                    cb.isTrue(link.get(TAG).get(IS_ACTIVE)));
            return cb.exists(carried);
        };
    }

    /**
     * Keeps the tasks at any of the given priorities.
     *
     * @param priorities the priorities to keep, never empty
     * @return the specification
     */
    public static Specification<Task> withPriorityIn(Collection<TaskPriority> priorities) {
        return (root, query, cb) -> root.get(PRIORITY).in(priorities);
    }

    /**
     * Keeps the tasks that are, or are not, past their due date. Derived rather than stored: the
     * only alternative is a flag that is wrong for every row between the due moment and the next
     * time something writes to it.
     *
     * @param now     the cutoff, from the application clock
     * @param overdue true for the ones past it, false for the ones that are not
     * @return the specification
     */
    public static Specification<Task> overdue(LocalDateTime now, boolean overdue) {
        return (root, query, cb) -> {
            // Only a chore still waiting to be done can be late; a completed one that was late is
            // history, and an undated one has nothing to be late for.
            Predicate late = cb.and(
                    cb.lessThan(root.get(NEXT_DUE_DATE), now),
                    root.get(STATUS).in(TaskStatus.OPEN));
            if (overdue) {
                return late;
            }
            // The undated case is spelled out rather than left to the negation. Comparing a null
            // due date yields unknown, and NOT unknown is still unknown, so an undated chore would
            // be dropped from both sides of its own filter.
            return cb.or(cb.isNull(root.get(NEXT_DUE_DATE)), cb.not(late));
        };
    }

    /**
     * Keeps the tasks whose name contains the given text, matched case-insensitively. The
     * description is deliberately not searched: a hit the card does not show reads as a bug.
     *
     * @param needle the text to match, already trimmed and non-empty
     * @return the specification
     */
    public static Specification<Task> nameContains(String needle) {
        return (root, query, cb) -> cb.like(
                cb.lower(root.get(NAME)), "%" + needle.toLowerCase(Locale.ROOT) + "%");
    }

    /**
     * Fetches the household and the people behind both memberships, since the response renders
     * their names rather than their ids.
     *
     * @return the specification, which adds no predicate of its own
     */
    public static Specification<Task> withPeopleFetched() {
        return (root, query, cb) -> {
            // Not on the count query: a fetch join there leaves the fetched association with no
            // owner in the selection and Hibernate rejects the statement.
            if (!Long.class.equals(query.getResultType())) {
                root.fetch(GROUP, JoinType.LEFT);
                root.fetch(CREATED_BY, JoinType.LEFT).fetch(USER, JoinType.LEFT);
                root.fetch(ASSIGNED_TO, JoinType.LEFT).fetch(USER, JoinType.LEFT);
                root.fetch(DEFAULT_ASSIGNEE, JoinType.LEFT).fetch(USER, JoinType.LEFT);
            }
            return null;
        };
    }

    /**
     * Orders the board. Held here rather than on the {@code Pageable} because one ordering cannot
     * be named as a property at all: priority is stored as a string, so sorting the column gives
     * HIGH, LOW, MEDIUM.
     *
     * @param sort the ordering asked for
     * @return the specification, which adds no predicate of its own
     */
    public static Specification<Task> ordered(TaskSort sort) {
        return (root, query, cb) -> {
            if (Long.class.equals(query.getResultType())) {
                return null;
            }
            List<Order> order = new ArrayList<>(orderFor(sort, root, cb));
            // Every ordering ends on the row id. Without a unique last key two tasks that tie can
            // come back in either order, and under page numbers that means one of them appearing
            // on both pages while the other appears on neither.
            order.add(cb.asc(root.get(ID_TASK)));
            query.orderBy(order);
            return null;
        };
    }

    private static List<Order> orderFor(TaskSort sort, Root<Task> root, CriteriaBuilder cb) {
        Expression<String> name = root.get(NAME);
        Expression<LocalDateTime> due = root.get(NEXT_DUE_DATE);
        // Postgres puts nulls last on ASC and first on DESC, so an undated chore would lead the
        // "latest due" board. Sorting on "is it undated" first settles it either way.
        Order undatedLast = cb.asc(cb.selectCase()
                .when(cb.isNull(due), 1)
                .otherwise(0)
                .as(Integer.class));

        return switch (sort == null ? TaskSort.DUE_ASC : sort) {
            case DUE_ASC -> List.of(undatedLast, cb.asc(due), cb.asc(name));
            case DUE_DESC -> List.of(undatedLast, cb.desc(due), cb.asc(name));
            case PRIORITY_HIGH_FIRST -> List.of(cb.asc(priorityRank(root, cb)), undatedLast, cb.asc(due));
            case CREATED_DESC -> List.of(cb.desc(root.get(DATE_CREATED)));
            case NAME_ASC -> List.of(cb.asc(cb.lower(name)));
        };
    }

    // LOW is spelled out so otherwise() catches only an unset priority, which sorts last.
    // Left implicit it would rank with LOW: a CASE comparing null matches no branch, silently.
    private static Expression<Integer> priorityRank(Root<Task> root, CriteriaBuilder cb) {
        return cb.<TaskPriority, Integer>selectCase(root.get(PRIORITY))
                .when(TaskPriority.HIGH, 0)
                .when(TaskPriority.MEDIUM, 1)
                .when(TaskPriority.LOW, 2)
                .otherwise(3);
    }
}
