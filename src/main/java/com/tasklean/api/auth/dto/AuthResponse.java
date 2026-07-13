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

    /** Registration response — no JWT, includes message about verification. */
    public static AuthResponse pendingVerification(User user) {
        return AuthResponse.builder()
                .uid(user.getUid())
                .email(user.getEmail())
                .name(user.getName())
                .message("Verification code sent to your email")
                .build();
    }
}
