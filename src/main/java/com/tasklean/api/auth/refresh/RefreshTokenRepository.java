package com.tasklean.api.auth.refresh;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
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
     * Revokes all active (non-revoked) refresh tokens issued on a device (per-device logout).
     *
     * @param deviceId the device's internal id
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.device.idDevice = :deviceId AND rt.isRevoked = false")
    void revokeAllByDeviceId(Long deviceId);

    /**
     * Returns the ids of a user's devices that currently have a live session (a non-revoked,
     * unexpired refresh token).
     *
     * @param userId the user's internal id
     * @param now    the current time; tokens expiring before this are treated as dead
     * @return the device ids with an active session
     */
    @Query("SELECT DISTINCT rt.device.idDevice FROM RefreshToken rt WHERE rt.user.idUser = :userId "
            + "AND rt.device IS NOT NULL AND rt.isRevoked = false AND rt.expiresAt > :now")
    List<Long> findActiveDeviceIds(Long userId, LocalDateTime now);

    /**
     * Deletes tokens that are revoked or already expired.
     *
     * @param now the current time; tokens expiring before this are deleted
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.isRevoked = true OR rt.expiresAt < :now")
    void deleteExpiredAndRevoked(LocalDateTime now);
}
