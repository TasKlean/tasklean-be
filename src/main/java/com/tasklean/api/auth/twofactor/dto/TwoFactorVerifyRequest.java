package com.tasklean.api.auth.twofactor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request body for finishing a sign-in that needs a second factor: the opaque challenge token the
 * login response returned, plus a code from the authenticator app or one recovery code.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TwoFactorVerifyRequest {

    @NotBlank(message = "Challenge token is required")
    private String challengeToken;

    @NotBlank(message = "Code is required")
    private String code;
}
