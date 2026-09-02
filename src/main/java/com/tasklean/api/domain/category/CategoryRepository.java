package com.tasklean.api.domain.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access for task categories. */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByGroupIdGroupAndIsActiveTrue(Long groupId);

    boolean existsByGroupIdGroupAndName(Long groupId, String name);
}
