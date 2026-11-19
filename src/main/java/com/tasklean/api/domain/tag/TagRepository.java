package com.tasklean.api.domain.tag;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Data access for tags. */
@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    /**
     * One live tag by id, for every path that answers with it or changes it.
     *
     * @param id the tag id
     * @return the tag, or empty if it does not exist or has been deleted
     */
    // The response names the household by uid, which is not an id and so is not served
    // by the proxy. The resolvers keep the inherited findById, which stays graph-free.
    @EntityGraph(attributePaths = "group")
    Optional<Tag> findByIdTagAndIsActiveTrue(Long id);

    /**
     * A household's live tags whose name contains the given text, ordered by name. The search is
     * passed as an empty string rather than omitted when a caller asks for the whole list, since
     * {@code like '%%'} matches every row and saves a second query for the unfiltered case.
     *
     * @param groupId the group id
     * @param q       the text to match anywhere in the name, or an empty string for all of them
     * @return the matching tags
     */
    @Query("""
            select t
            from Tag t
            where t.group.idGroup = :groupId and t.isActive = true
              and lower(t.name) like lower(concat('%', :q, '%'))
            order by t.name
            """)
    @EntityGraph(attributePaths = "group")
    List<Tag> searchActive(Long groupId, String q);

    /**
     * One tag of a household by name, matched case-insensitively and <strong>including a deleted
     * one</strong> — the same rule as categories, and the reason a member typing "kitchen" where
     * "Kitchen" exists lands on the tag that is already there.
     *
     * @param groupId the group id
     * @param name    the name to match
     * @return the tag, if the household has ever had one by that name
     */
    @Query("select t from Tag t where t.group.idGroup = :groupId and lower(t.name) = lower(:name)")
    Optional<Tag> findByGroupAndName(Long groupId, String name);
}
