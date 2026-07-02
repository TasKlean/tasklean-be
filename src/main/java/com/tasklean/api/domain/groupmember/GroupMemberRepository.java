package com.tasklean.api.domain.groupmember;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    List<GroupMember> findByGroupIdGroupAndIsActiveTrue(Long groupId);

    List<GroupMember> findByUserIdUserAndIsActiveTrue(Long userId);

    Optional<GroupMember> findByUserIdUserAndGroupIdGroup(Long userId, Long groupId);

    boolean existsByUserIdUserAndGroupIdGroup(Long userId, Long groupId);
}
