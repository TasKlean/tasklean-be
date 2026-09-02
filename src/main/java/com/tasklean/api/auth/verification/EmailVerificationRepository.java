package com.tasklean.api.auth.verification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/** Data access for email verification codes. */
@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findTopByUserIdUserAndCodeAndExpiresAtAfterOrderByDateCreatedDesc(
            Long userId, String code, LocalDateTime now);

    void deleteByUserIdUser(Long userId);
}
