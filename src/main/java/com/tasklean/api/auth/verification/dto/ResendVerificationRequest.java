package com.tasklean.api.auth.verification.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResendVerificationRequest {

    @NotBlank
    @Email
    @Size(min = 1, max = FieldLimits.EMAIL)
    private String email;
}
