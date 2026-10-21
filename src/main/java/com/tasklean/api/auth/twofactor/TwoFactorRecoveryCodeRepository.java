package com.tasklean.api.auth.twofactor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access for a user's recovery codes: matching an unused one at sign-in, reporting how many
 * are left, and clearing the set when it is regenerated or two-factor is turned off.
 */
@Repository
public interface TwoFactorRecoveryCodeRepository extends JpaRepository<TwoFactorRecoveryCode, Long> {

    Optional<TwoFactorRecoveryCode> findByUserIdUserAndCodeHashAndUsedAtIsNull(Long userId, String codeHash);

    long countByUserIdUserAndUsedAtIsNull(Long userId);

    void deleteByUserIdUser(Long userId);
}
