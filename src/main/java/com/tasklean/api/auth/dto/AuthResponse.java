package com.tasklean.api.auth.dto;

import com.tasklean.api.domain.user.User;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String token;
    private String refreshToken;
    private String uid;
    private String email;
    private String name;
    private String message;

    // True when the password/Google step succeeded but a second factor is still required; the
    // token fields stay empty until POST /api/auth/2fa/verify completes the sign-in.
    private boolean twoFactorRequired;

    private String challengeToken;

    public static AuthResponse from(User user, String token, String refreshToken) {
        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .uid(user.getUid())
                .email(user.getEmail())
                .name(user.getName())
                .build();
    }

    /**
     * Sign-in response for an account with two-factor enabled: no tokens and no profile fields
     * (nothing is handed out before the second factor is proven), just the opaque challenge token
     * the client posts back with the code.
     */
    public static AuthResponse twoFactorRequired(String challengeToken) {
        return AuthResponse.builder()
                .twoFactorRequired(true)
                .challengeToken(challengeToken)
                .message("Enter the code from your authenticator app to finish signing in.")
                .build();
    }

    /**
     * Registration response — no JWT, and deliberately neutral: it echoes only the submitted
     * email so the reply is identical whether or not the address was already registered
     * (anti-enumeration). No uid/name, which would reveal an existing account.
     */
    public static AuthResponse registrationPending(String email) {
        return AuthResponse.builder()
                .email(email)
                .message("If that email isn't already registered, we've sent a verification code. Please check your inbox.")
                .build();
    }
}
