package com.tasklean.api.domain.groupmember;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Data access for group memberships (the user↔group join with role). */
@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    List<GroupMember> findByGroupIdGroupAndIsActiveTrue(Long groupId);

    /**
     * A group's active members with the user behind each one, and behind each attribution, already
     * loaded. The fetch is part of the query so the response maps without an open session, and the
     * list costs one query instead of one per member per hop.
     *
     * @param groupUid the group's public UID
     * @return the group's active memberships
     */
    @EntityGraph(attributePaths = {"user", "addedBy.user", "removedBy.user"})
    List<GroupMember> findByGroupUidAndIsActiveTrue(String groupUid);

    /**
     * Everybody who was ever in the group, loaded as {@link #findByGroupUidAndIsActiveTrue}. A
     * membership is deactivated rather than deleted, so this is the whole history of the household.
     *
     * @param groupUid the group's public UID
     * @return every membership of the group, active or not
     */
    @EntityGraph(attributePaths = {"user", "addedBy.user", "removedBy.user"})
    List<GroupMember> findByGroupUid(String groupUid);

    /**
     * The people who have left the group, loaded as {@link #findByGroupUidAndIsActiveTrue}.
     *
     * @param groupUid the group's public UID
     * @return the group's inactive memberships
     */
    @EntityGraph(attributePaths = {"user", "addedBy.user", "removedBy.user"})
    List<GroupMember> findByGroupUidAndIsActiveFalse(String groupUid);

    /**
     * One membership with the same associations loaded as {@link #findByGroupUidAndIsActiveTrue}.
     * Separate from {@link #findByUid} because that one also serves the authorization checks, which
     * read only ids and would pay for joins they never look at.
     *
     * @param uid the membership's public UID
     * @return the membership, or empty if none has that UID
     */
    @EntityGraph(attributePaths = {"user", "addedBy.user", "removedBy.user"})
    Optional<GroupMember> findDetailedByUid(String uid);

    /**
     * The user's active memberships with their groups already loaded, for the "my groups" list.
     * Same reason as above: the group is read for its name, not its id, so fetching it in the query
     * keeps the mapping independent of any session and turns one query per group into none.
     *
     * @param userId the user's internal id
     * @return the user's active memberships
     */
    @EntityGraph(attributePaths = "group")
    List<GroupMember> findByUserIdUserAndIsActiveTrue(Long userId);

    Optional<GroupMember> findByUserIdUserAndGroupIdGroup(Long userId, Long groupId);

    Optional<GroupMember> findByUserUidAndGroupUid(String userUid, String groupUid);

    Optional<GroupMember> findByUid(String uid);

    long countByGroupIdGroupAndRoleAndIsActiveTrue(Long groupId, GroupRole role);

    long countByGroupIdGroupAndIsActiveTrue(Long groupId);

    /**
     * Active member counts for several groups at once, so a list of groups costs one query
     * instead of one per group.
     *
     * @param groupIds the group ids to count members for
     * @return one row per group that has at least one active member
     */
    @Query("""
            select m.group.idGroup as groupId, count(m) as total
            from GroupMember m
            where m.isActive = true and m.group.idGroup in :groupIds
            group by m.group.idGroup
            """)
    List<GroupMemberCount> countActiveByGroupIds(Collection<Long> groupIds);

    /** One group's active member count, as returned by {@link #countActiveByGroupIds}. */
    interface GroupMemberCount {
        Long getGroupId();

        Long getTotal();
    }
}
