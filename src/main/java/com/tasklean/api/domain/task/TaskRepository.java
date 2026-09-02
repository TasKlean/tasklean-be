package com.tasklean.api.domain.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Data access for tasks, including UID lookup and group/assignee/status filters. */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    Optional<Task> findByUid(String uid);

    List<Task> findByGroupIdGroupAndIsActiveTrue(Long groupId);

    List<Task> findByAssignedToIdGroupMemberAndIsActiveTrue(Long groupMemberId);

    List<Task> findByStatusAndIsActiveTrue(String status);
}
