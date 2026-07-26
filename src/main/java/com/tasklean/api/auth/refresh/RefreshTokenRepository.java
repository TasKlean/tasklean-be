package com.tasklean.api.auth.refresh;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenAndIsRevokedFalse(String token);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.user.idUser = :userId AND rt.isRevoked = false")
    void revokeAllByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.isRevoked = true OR rt.expiresAt < :now")
    int deleteExpiredAndRevoked(LocalDateTime now);
}
