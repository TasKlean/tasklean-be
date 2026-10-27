package com.tasklean.api.auth.twofactor.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(min = 1, max = FieldLimits.TOKEN)
    private String challengeToken;

    @NotBlank(message = "Code is required")
    @Size(min = 1, max = FieldLimits.CODE)
    private String code;
}
