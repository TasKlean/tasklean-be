package com.tasklean.api.domain.tag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access for the task–tag join table. */
@Repository
public interface TaskTagRepository extends JpaRepository<TaskTag, Long> {

    List<TaskTag> findByTaskIdTask(Long taskId);

    List<TaskTag> findByTagIdTag(Long tagId);

    void deleteByTaskIdTaskAndTagIdTag(Long taskId, Long tagId);

    boolean existsByTaskIdTaskAndTagIdTag(Long taskId, Long tagId);
}
