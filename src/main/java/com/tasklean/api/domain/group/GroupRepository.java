package com.tasklean.api.domain.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Data access for groups, including UID and invite-code lookups. */
@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    Optional<Group> findByUid(String uid);

    Optional<Group> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);
}
