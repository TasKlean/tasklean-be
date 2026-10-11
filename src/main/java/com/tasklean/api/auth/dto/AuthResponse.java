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
