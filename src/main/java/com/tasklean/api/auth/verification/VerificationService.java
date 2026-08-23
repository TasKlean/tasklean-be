package com.tasklean.api.auth.verification;

import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.auth.refresh.RefreshTokenService;
import com.tasklean.api.auth.verification.dto.ResendVerificationRequest;
import com.tasklean.api.auth.verification.dto.VerifyEmailRequest;
import com.tasklean.api.common.email.EmailService;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Handles email verification code generation, sending, and validation.
 * Codes are 6-digit numeric strings with 5-minute expiry.
 */
@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final int CODE_EXPIRY_MINUTES = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    /** Generates a 6-digit code, persists it, and sends it to the user's email. */
    @Transactional
    public void createAndSendVerification(User user) {
        verificationRepository.deleteByUserIdUser(user.getIdUser());

        String code = generateCode();

        EmailVerification verification = EmailVerification.builder()
                .user(user)
                .code(code)
                .expiresAt(LocalDateTime.now(clock).plusMinutes(CODE_EXPIRY_MINUTES))
                .build();

        verificationRepository.save(verification);
        emailService.sendVerificationEmail(user.getEmail(), code);
    }

    /** Validates the code, marks the user as verified, cleans all user codes and returns a JWT. */
    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .filter(u -> !u.getIsEmailVerified())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or code"));

        verificationRepository.findTopByUserIdUserAndCodeAndExpiresAtAfterOrderByDateCreatedDesc(
                user.getIdUser(), request.getCode(), LocalDateTime.now(clock)
        ).orElseThrow(() -> new BadCredentialsException("Invalid email or code"));

        user.setIsEmailVerified(true);
        userRepository.save(user);

        // Clean up all codes for this user
        verificationRepository.deleteByUserIdUser(user.getIdUser());

        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);
        return AuthResponse.from(user, token, refreshToken);
    }

    /** Resends a verification code. */
    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        userRepository.findByEmail(request.getEmail())
                .filter(user -> !user.getIsEmailVerified())
                .ifPresent(this::createAndSendVerification);
    }

    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
