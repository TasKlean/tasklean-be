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
    private String uid;
    private String email;
    private String name;

    public static AuthResponse from(User user, String token) {
        return AuthResponse.builder()
                .token(token)
                .uid(user.getUid())
                .email(user.getEmail())
                .name(user.getName())
                .build();
    }
}
