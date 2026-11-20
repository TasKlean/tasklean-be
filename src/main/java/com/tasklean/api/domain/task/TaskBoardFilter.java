package com.tasklean.api.domain.task;

import com.tasklean.api.common.exception.InvalidRequestException;
import com.tasklean.api.common.validation.FieldLimits;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.task.dto.TaskBoardFilterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns what a caller asked of a task board into the predicate that reads it and the page to read.
 * Its own component so a new filter is a method here rather than another parameter threaded through
 * the service, and so the household scope it composes onto stays untouched.
 */
@Component
@RequiredArgsConstructor
class TaskBoardFilter {

    private final GroupMemberRepository groupMemberRepository;
    private final Clock clock;

    /**
     * Narrows a household's board by what the request asked for.
     *
     * @param groupId the household whose board is being read
     * @param query   the request's filter parameters
     * @return the household's live tasks narrowed by every filter the caller sent
     * @throws InvalidRequestException if the request asks for one assignee and no assignee at once
     */
    public Specification<Task> narrow(Long groupId, TaskBoardFilterRequest query) {
        Specification<Task> spec = TaskBoardSpecs.liveInGroup(groupId)
                .and(TaskBoardSpecs.withStatusIn(statuses(query.getStatus())))
                .and(TaskBoardSpecs.withPeopleFetched())
                .and(TaskBoardSpecs.ordered(query.getSort()));

        spec = byAssignee(spec, groupId, query);
        spec = and(spec, query.getCategoryId(), TaskBoardSpecs::inCategory);
        spec = and(spec, query.getTagId(), TaskBoardSpecs::carryingTag);

        Set<TaskPriority> priorities = priorities(query.getPriority());
        if (!priorities.isEmpty()) {
            spec = spec.and(TaskBoardSpecs.withPriorityIn(priorities));
        }
        if (query.getOverdue() != null) {
            spec = spec.and(TaskBoardSpecs.overdue(LocalDateTime.now(clock), query.getOverdue()));
        }
        String needle = trimmed(query.getQ());
        if (needle != null) {
            spec = spec.and(TaskBoardSpecs.nameContains(needle));
        }
        return spec;
    }

    /**
     * The page the request asked for. Carries no sort of its own — the ordering is part of the
     * specification, since one of the orderings cannot be named as a property.
     *
     * @param query the request's paging parameters
     * @return the page to read
     */
    public Pageable pageFor(TaskBoardFilterRequest query) {
        // "page=" with nothing after it binds to null rather than to the field's initializer, so
        // the defaults live here as well as on the request.
        int page = query.getPage() == null ? 0 : query.getPage();
        int size = query.getSize() == null ? FieldLimits.TASK_PAGE_SIZE : query.getSize();
        return PageRequest.of(page, size);
    }

    private Specification<Task> byAssignee(Specification<Task> spec, Long groupId,
                                           TaskBoardFilterRequest query) {
        boolean pool = Boolean.TRUE.equals(query.getUnassigned());
        String memberUid = trimmed(query.getAssignedToUid());
        if (pool && memberUid != null) {
            throw new InvalidRequestException(
                    "Ask for one member's chores or for the unassigned ones, not both");
        }
        if (pool) {
            return spec.and(TaskBoardSpecs.unassigned());
        }
        if (memberUid == null) {
            return spec;
        }

        // A uid naming nobody, or somebody in another household, is an empty board rather than a
        // 404: the board is a filter, and a filter that matched nothing is not a missing resource.
        return groupMemberRepository.findByUid(memberUid)
                .filter(member -> member.getGroup().getIdGroup().equals(groupId))
                .map(GroupMember::getIdGroupMember)
                .map(id -> spec.and(TaskBoardSpecs.assignedTo(id)))
                .orElseGet(() -> spec.and(matchesNothing()));
    }

    // An absent status is the board being a to-do list: the chores still waiting to be done.
    private Set<TaskStatus> statuses(List<TaskStatus> asked) {
        if (asked == null) {
            return TaskStatus.OPEN;
        }
        Set<TaskStatus> kept = asked.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        // Asking for no state at all is asking for no narrowing, the same as naming every state.
        return kept.isEmpty() ? TaskStatus.OPEN : kept;
    }

    private Set<TaskPriority> priorities(List<TaskPriority> asked) {
        return asked == null ? Set.of()
                : asked.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    // Always false, so a filter that resolved to nobody answers an empty board without a second
    // query to discover that.
    private Specification<Task> matchesNothing() {
        return (root, query, cb) -> cb.disjunction();
    }

    private Specification<Task> and(Specification<Task> spec, Long value,
                                    Function<Long, Specification<Task>> of) {
        return value == null ? spec : spec.and(of.apply(value));
    }

    private String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
