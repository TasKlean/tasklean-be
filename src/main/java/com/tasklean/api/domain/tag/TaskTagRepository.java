package com.tasklean.api.domain.tag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/** Data access for the task–tag join table. */
@Repository
public interface TaskTagRepository extends JpaRepository<TaskTag, Long> {

    List<TaskTag> findByTaskIdTask(Long taskId);

    List<TaskTag> findByTagIdTag(Long tagId);

    void deleteByTaskIdTaskAndTagIdTag(Long taskId, Long tagId);

    boolean existsByTaskIdTaskAndTagIdTag(Long taskId, Long tagId);

    /**
     * The live tag links of several tasks at once, with each tag and its household loaded.
     * One query for a whole board page rather than one per task, which is the difference
     * between two queries and forty.
     *
     * @param taskIds the task ids, never empty
     * @return the links, each carrying its tag
     */
    @Query("""
            select tt
            from TaskTag tt
            join fetch tt.tag tag
            join fetch tag.group
            where tt.task.idTask in :taskIds and tag.isActive = true
            """)
    List<TaskTag> findActiveByTasks(Collection<Long> taskIds);
}
