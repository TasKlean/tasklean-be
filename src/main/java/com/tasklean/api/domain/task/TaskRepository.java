package com.tasklean.api.domain.task;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Data access for tasks. The board reads through specifications rather than a declared finder:
 * it is a household scope plus whichever filters the caller sent, and spelling every combination
 * out as its own query is what the specification seam avoids.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    /**
     * One task by uid, whether it is still active. Unfiltered and graph-free on purpose:
     * this is what {@code TaskSecurity} resolves, and it reads ids only. Keeping a deleted task
     * visible here is what makes a former member's 404 distinct from a stranger's 403.
     *
     * @param uid the task's public UID
     * @return the task, or empty if no task has that UID
     */
    Optional<Task> findByUid(String uid);

    /**
     * One live task by uid, with the group and the people behind its author and assignee loaded —
     * {@code TaskResponse} reads their names, not their ids, and no session outlives the service
     * call. The category is left as a proxy, since only its id is published.
     *
     * @param uid the task's public UID
     * @return the task, or empty if no active task has that UID
     */
    @EntityGraph(attributePaths = {"group", "createdBy.user", "assignedTo.user"})
    Optional<Task> findDetailedByUidAndIsActiveTrue(String uid);

    /**
     * A live task by uid, for the paths that are about to change it.
     *
     * @param uid the task's public UID
     * @return the task, or empty if no active task has that UID
     */
    Optional<Task> findByUidAndIsActiveTrue(String uid);

    /**
     * One active task by uid, with its row locked until the transaction ends. Recording an
     * instance reads the schedule and then writes it forward, so two callers completing the same
     * chore at once would otherwise both read the same due date - one of them then losing to the
     * unique index as a 500 instead of being told the chore is already done.
     *
     * <p>Deliberately graph-free: {@code FOR UPDATE} cannot be applied to the nullable side of an
     * outer join, which is what fetching an optional assignee would make it.
     *
     * @param uid the task's public UID
     * @return the task, or empty if no active task has that uid
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.uid = :uid and t.isActive = true")
    Optional<Task> findForUpdateByUid(String uid);

    /**
     * How many live tasks are classified under a category. Archived and completed ones count:
     * they still carry the category on every share and history screen, so deleting it out from
     * under them would leave a classification nothing can resolve.
     *
     * @param categoryId the category id
     * @return the number of tasks still pointing at it
     */
    @Query("select count(t) from Task t where t.category.idCategory = :categoryId and t.isActive = true")
    long countActiveByCategory(Long categoryId);

    /**
     * The live tasks a membership is still on the hook for. Used when that membership ends, so the
     * household is not left with chores assigned to somebody who has gone.
     *
     * @param membershipId the membership's internal id
     * @param statuses     the states that still count as waiting to be done
     * @return the matching tasks
     */
    @Query("""
            select t
            from Task t
            where t.assignedTo.idGroupMember = :membershipId
              and t.isActive = true and t.status in :statuses
            """)
    List<Task> findOpenAssignedTo(Long membershipId, Collection<TaskStatus> statuses);
    /**
     * The uids of chores whose current round is already past, oldest first — the missed-round
     * sweep's work list.
     *
     * <p>Only recurring chores: a one-off has no rounds to miss, it is simply overdue, which the
     * board already says. Archived and soft-deleted ones are out, and {@link TaskStatus#OPEN} is
     * what "still waiting to be done" means everywhere else too.
     *
     * <p>Returns uids rather than rows because each one is then loaded and locked in its own
     * transaction; holding a lock across the whole batch would block the members doing the chores.
     *
     * @param cutoff the first instant that is not yet written off
     * @param limit  how many to return
     * @return the uids, oldest round first
     */
    @Query("""
            select t.uid from Task t
            where t.isActive = true
              and t.recurrenceType is not null
              and t.nextDueDate is not null
              and t.nextDueDate < :cutoff
              and t.status in :open
            order by t.nextDueDate, t.idTask
            """)
    List<String> findUidsWithRoundPast(LocalDateTime cutoff, Collection<TaskStatus> open, Limit limit);
}
