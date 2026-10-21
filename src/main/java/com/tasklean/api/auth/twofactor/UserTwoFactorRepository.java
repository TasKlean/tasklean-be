package com.tasklean.api.auth.twofactor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access for the one two-factor factor a user may have, always looked up by owning user.
 */
@Repository
public interface UserTwoFactorRepository extends JpaRepository<UserTwoFactor, Long> {

    Optional<UserTwoFactor> findByUserIdUser(Long userId);
}
