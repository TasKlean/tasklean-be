package com.tasklean.api.domain.task;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Data access for tasks. */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * One task by uid, whether or not it is still active. Unfiltered and graph-free on purpose:
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
}
