package com.tasklean.api.domain.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Data access for groups, including UID and invite-code lookups. */
@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    Optional<Group> findByUid(String uid);

    /**
     * Finds a group by uid, ignoring soft-deleted ones. Every service lookup uses this rather
     * than {@link #findByUid}: a deleted group is gone as far as the API is concerned. The
     * authorization checks keep the unfiltered one, so a member still gets 404 and not 403.
     *
     * @param uid the group's public UID
     * @return the group, or empty if there is none or it is soft-deleted
     */
    Optional<Group> findByUidAndIsActiveTrue(String uid);

    Optional<Group> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);
}
