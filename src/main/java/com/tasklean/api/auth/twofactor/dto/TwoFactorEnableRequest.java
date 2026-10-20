package com.tasklean.api.auth.twofactor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request body for confirming setup: the code currently shown by the authenticator app that was
 * just enrolled. No password - the pending secret itself is the proof.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TwoFactorEnableRequest {

    @NotBlank(message = "Code is required")
    private String code;
}
