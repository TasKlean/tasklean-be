package com.tasklean.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/** Request body for "Sign in with Google": the Google ID token plus the OIDC nonce secret. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleAuthRequest {

    @NotBlank(message = "ID token is required")
    private String idToken;

    // The raw per-login secret whose SHA-256 the client used as the ID token's nonce claim.
    @NotBlank(message = "Nonce is required")
    private String nonce;
}
