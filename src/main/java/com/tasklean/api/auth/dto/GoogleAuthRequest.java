package com.tasklean.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/** Request body for "Sign in with Google": the Google ID token obtained by the client. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleAuthRequest {

    @NotBlank(message = "ID token is required")
    private String idToken;
}
