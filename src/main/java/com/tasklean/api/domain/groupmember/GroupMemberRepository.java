package com.tasklean.api.domain.groupmember;

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

    List<GroupMember> findByGroupUidAndIsActiveTrue(String groupUid);

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
