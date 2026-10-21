package com.tasklean.api.auth;

import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.auth.refresh.RefreshTokenService;
import com.tasklean.api.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Re-issues the caller's session after a security-sensitive change (password change, two-factor
 * enabled or disabled): every existing session is revoked, then a fresh pair is minted for the
 * current device so the caller stays signed in here while all other devices must re-authenticate.
 */
@Component
@RequiredArgsConstructor
public class SessionRotator {

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    /**
     * Revokes every session of the user and issues a new access + refresh token pair bound to the
     * caller's current device.
     *
     * @param user the account whose sessions are rotated
     * @return the fresh auth response for the current device
     */
    public AuthResponse rotateCurrentSession(User user) {
        refreshTokenService.revokeAllUserTokens(user);
        // The new token is inserted after the bulk revoke, so it is not caught by it.
        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);
        return AuthResponse.from(user, accessToken, refreshToken);
    }
}
