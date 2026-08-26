package com.tasklean.api.auth.refresh;

import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.auth.refresh.dto.RefreshRequest;
import com.tasklean.api.config.JwtConfig;
import com.tasklean.api.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtConfig jwtConfig;
    private final Clock clock;

    @Transactional
    public String createRefreshToken(User user) {
        String rawToken = generateToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(hashToken(rawToken))
                .expiresAt(LocalDateTime.now(clock).plus(Duration.ofMillis(jwtConfig.getRefreshExpiration())))
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String hashedToken = hashToken(request.getRefreshToken());

        RefreshToken existing = refreshTokenRepository.findByTokenAndIsRevokedFalse(hashedToken)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (existing.getExpiresAt().isBefore(LocalDateTime.now(clock))) {
            existing.setIsRevoked(true);
            refreshTokenRepository.save(existing);
            throw new BadCredentialsException("Refresh token expired");
        }

        // Rotate: revoke old, issue new
        existing.setIsRevoked(true);
        refreshTokenRepository.save(existing);

        User user = existing.getUser();
        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = createRefreshToken(user);

        return AuthResponse.from(user, newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        String hashedToken = hashToken(request.getRefreshToken());

        RefreshToken token = refreshTokenRepository.findByTokenAndIsRevokedFalse(hashedToken)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        refreshTokenRepository.revokeAllByUserId(token.getUser().getIdUser());
    }

    @Transactional
    public void revokeAllUserTokens(User user) {
        refreshTokenRepository.revokeAllByUserId(user.getIdUser());
    }

    @Scheduled(fixedRate = 6 * 60 * 60 * 1000) // every 6 hours
    @Transactional
    public void purgeExpiredAndRevokedTokens() {
        refreshTokenRepository.deleteExpiredAndRevoked(LocalDateTime.now(clock));
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
