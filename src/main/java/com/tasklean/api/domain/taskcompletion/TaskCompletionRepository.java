package com.tasklean.api.domain.taskcompletion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskCompletionRepository extends JpaRepository<TaskCompletion, Long> {

    List<TaskCompletion> findByTaskIdTaskOrderByDateCompletedDesc(Long taskId);

    List<TaskCompletion> findByCompletedByIdGroupMemberOrderByDateCompletedDesc(Long groupMemberId);
}
