package com.tasklean.api.domain.category;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Data access for task categories. */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * One live category by id, for every path that answers with it or changes it.
     *
     * @param id the category id
     * @return the category, or empty if it does not exist or has been deleted
     */
    // The response names the household by uid, which is not an id and so is not served
    // by the proxy. The resolvers keep the inherited findById, which stays graph-free.
    @EntityGraph(attributePaths = "group")
    Optional<Category> findByIdCategoryAndIsActiveTrue(Long id);

    /**
     * A household's live categories, ordered by name.
     *
     * @param groupId the group id
     * @return the categories
     */
    @EntityGraph(attributePaths = "group")
    List<Category> findByGroupIdGroupAndIsActiveTrueOrderByName(Long groupId);

    /**
     * One category of a household by name, matched case-insensitively and <strong>including a
     * deleted one</strong>. Both halves matter: the unique index ignores case, so a differently
     * cased name is the same name, and it covers deleted rows, so a name that looks free is not —
     * which is why re-creating a deleted category revives it rather than inserting a twin.
     *
     * @param groupId the group id
     * @param name    the name to match
     * @return the category, if the household has ever had one by that name
     */
    @Query("select c from Category c where c.group.idGroup = :groupId and lower(c.name) = lower(:name)")
    Optional<Category> findByGroupAndName(Long groupId, String name);
}
