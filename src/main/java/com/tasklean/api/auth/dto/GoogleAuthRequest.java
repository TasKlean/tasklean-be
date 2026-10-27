package com.tasklean.api.auth.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/** Request body for "Sign in with Google": the Google ID token plus the OIDC nonce secret. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleAuthRequest {

    @NotBlank(message = "ID token is required")
    @Size(min = 1, max = FieldLimits.GOOGLE_ID_TOKEN)
    private String idToken;

    // The raw per-login secret whose SHA-256 the client used as the ID token's nonce claim.
    @NotBlank(message = "Nonce is required")
    @Size(min = 1, max = FieldLimits.NONCE)
    private String nonce;
}
