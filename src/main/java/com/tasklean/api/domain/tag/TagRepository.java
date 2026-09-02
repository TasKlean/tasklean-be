package com.tasklean.api.domain.tag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access for tags. */
@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findByGroupIdGroupAndIsActiveTrue(Long groupId);

    boolean existsByGroupIdGroupAndName(Long groupId, String name);
}
