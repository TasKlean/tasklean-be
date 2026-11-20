package com.tasklean.api.domain.taskcompletion;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Data access for the instances of a task - what was due, and what became of it. A task's history
 * is keyset-paged rather than read whole: a daily chore accumulates a row a day for as long as the
 * household keeps it.
 *
 * <p>It pages on {@code (date_completed, id_task_completion)}, not on the due date. A keyset has to
 * order by something that cannot be null: comparing a null due date yields unknown, so a row that
 * has one - every instance of an undated one-off, and every row written before V32 - would be
 * unreachable by any cursor, silently, with the page before it reporting no next page at all.
 */
@Repository
public interface TaskCompletionRepository extends JpaRepository<TaskCompletion, Long> {

    /**
     * One instance by id, whether or not it is still active, with the people behind it loaded.
     * Unfiltered so an undone instance answers 404 from the service rather than 403 from the
     * resolver, the same split every other soft-deleted row here follows.
     *
     * @param id the instance's id
     * @return the instance, or empty if there is no such row
     */
    @EntityGraph(attributePaths = {"task.group", "attributedTo.user"})
    Optional<TaskCompletion> findDetailedByIdTaskCompletion(Long id);

    /**
     * One instance by id with its task loaded, for the authorization resolvers. The task is fetched
     * rather than left a proxy because the rule needs the household behind it, and a resolver runs
     * with no session open: from a loaded task {@code getGroup().getIdGroup()} is served by the
     * proxy, but from a proxied one it is a second hop that has to load the row.
     *
     * @param id the instance id
     * @return the instance, active or not, or empty if there is no such row
     */
    @EntityGraph(attributePaths = "task")
    Optional<TaskCompletion> findWithTaskByIdTaskCompletion(Long id);

    /**
     * One active instance by id, graph-free - for the paths that only need to check and write it.
     *
     * @param id the instance's id
     * @return the instance, or empty if there is no such active row
     */
    Optional<TaskCompletion> findByIdTaskCompletionAndIsActiveTrue(Long id);

    /**
     * The first page of one task's history, most recently recorded first.
     *
     * <p>Two finders rather than one with a {@code :completedAt is null} guard: an absent cursor has
     * to contribute no predicate at all, and Postgres cannot infer a bare parameter's type inside
     * such a test - it refuses the statement outright.
     *
     * @param taskId the task whose history to read
     * @param limit  how many rows to read
     * @return the instances, most recently recorded first
     */
    @EntityGraph(attributePaths = {"task", "attributedTo.user"})
    @Query("""
            select tc from TaskCompletion tc
            where tc.task.idTask = :taskId and tc.isActive = true
            order by tc.dateCompleted desc, tc.idTaskCompletion desc
            """)
    List<TaskCompletion> findHistory(Long taskId, Limit limit);

    /**
     * A later page of one task's history, resuming after the position the cursor named.
     *
     * @param taskId      the task whose history to read
     * @param completedAt the moment to resume after
     * @param id          the row id to break a tie on that moment
     * @param limit       how many rows to read
     * @return the instances after that position, most recently recorded first
     */
    @EntityGraph(attributePaths = {"task", "attributedTo.user"})
    @Query("""
            select tc from TaskCompletion tc
            where tc.task.idTask = :taskId and tc.isActive = true
              and (tc.dateCompleted < :completedAt
                   or (tc.dateCompleted = :completedAt and tc.idTaskCompletion < :id))
            order by tc.dateCompleted desc, tc.idTaskCompletion desc
            """)
    List<TaskCompletion> findHistoryAfter(Long taskId, LocalDateTime completedAt, Long id, Limit limit);

    /**
     * How many instances a task has recorded, undone ones excluded.
     *
     * @param taskId the task
     * @return the count
     */
    @Query("select count(tc) from TaskCompletion tc where tc.task.idTask = :taskId and tc.isActive = true")
    long countByTask(Long taskId);

    /**
     * The most recently recorded instance of a task - the only one an undo may take back, since
     * taking back an earlier one would put the schedule behind instances that already happened.
     *
     * @param taskId the task
     * @return that instance, or empty if the task has none
     */
    @Query("""
            select tc from TaskCompletion tc
            where tc.task.idTask = :taskId and tc.isActive = true
            order by tc.dateCompleted desc, tc.idTaskCompletion desc
            limit 1
            """)
    Optional<TaskCompletion> findLatestByTask(Long taskId);

    /**
     * Whether a task already has an active instance recorded for a due date. Reachable when the
     * anchor is moved back onto a round that was already recorded - an admin editing
     * {@code nextDueDate} is the way that happens - and this read is what turns the unique index's
     * 500 into a 409.
     *
     * @param taskId  the task
     * @param dueDate the instance's due date
     * @return {@code true} if that instance is already recorded
     */
    boolean existsByTaskIdTaskAndDueDateAndIsActiveTrue(Long taskId, LocalDateTime dueDate);
}
