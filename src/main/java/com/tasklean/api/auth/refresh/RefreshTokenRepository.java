package com.tasklean.api.auth.refresh;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Data access for refresh tokens, including bulk revocation on logout and the
 * scheduled cleanup of expired/revoked tokens.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenAndIsRevokedFalse(String token);

    /**
     * Revokes all active (non-revoked) refresh tokens for a user.
     *
     * @param userId the user's internal id
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.user.idUser = :userId AND rt.isRevoked = false")
    void revokeAllByUserId(Long userId);

    /**
     * Deletes tokens that are revoked or already expired.
     *
     * @param now the current time; tokens expiring before this are deleted
     * @return the number of tokens deleted
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.isRevoked = true OR rt.expiresAt < :now")
    int deleteExpiredAndRevoked(LocalDateTime now);
}
