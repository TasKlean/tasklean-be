package com.tasklean.api.auth.twofactor;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Data access for pending sign-in challenges: lookup by hashed token, and deletion of the ones
 * that can no longer be used.
 */
@Repository
public interface TwoFactorChallengeRepository extends JpaRepository<TwoFactorChallenge, Long> {

    // Verification runs outside a transaction (so a failed attempt still commits), so the owning
    // user is fetched with the challenge rather than left as a lazy proxy.
    @EntityGraph(attributePaths = "user")
    Optional<TwoFactorChallenge> findByTokenHashAndConsumedAtIsNull(String tokenHash);

    void deleteByUserIdUser(Long userId);

    /**
     * Deletes challenges that are expired or already consumed.
     *
     * @param now the cutoff to compare expiry against
     */
    @Modifying
    @Query("DELETE FROM TwoFactorChallenge c WHERE c.expiresAt < :now OR c.consumedAt IS NOT NULL")
    void deleteExpiredAndConsumed(LocalDateTime now);
}
