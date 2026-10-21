package com.tasklean.api.auth.twofactor;

import com.tasklean.api.auth.twofactor.provider.TwoFactorProviderType;
import com.tasklean.api.common.BaseEntity;
import com.tasklean.api.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_two_factor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTwoFactor extends BaseEntity {

    // Recorded per row so a future algorithm change can apply to new enrollments only.
    public static final String DEFAULT_DIGEST = "SHA1";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_user_two_factor")
    private Long idUserTwoFactor;

    // One factor per user: @ManyToOne with a unique join column, matching how user_settings
    // models its 1:1 while the DB constraint enforces it.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private TwoFactorProviderType provider = TwoFactorProviderType.LOCAL;

    // Set by a hosted provider instead of a secret; null for LOCAL.
    @Column(name = "factor_ref")
    private String factorRef;

    // AES-GCM ciphertext from SecretCipher, never the raw secret.
    @Column(name = "secret_encrypted", columnDefinition = "TEXT")
    private String secretEncrypted;

    @Builder.Default
    @Column(name = "digest", nullable = false, length = 10)
    private String digest = DEFAULT_DIGEST;

    // False until the user proves they can produce a code, so an abandoned setup changes nothing.
    @Builder.Default
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = false;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    // Highest accepted TOTP step; a code at or below it is a replay.
    @Column(name = "last_used_step")
    private Long lastUsedStep;
}
