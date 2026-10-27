package com.tasklean.api.auth.refresh.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefreshRequest {

    @NotBlank(message = "Refresh token is required")
    @Size(min = 1, max = FieldLimits.TOKEN)
    private String refreshToken;
}
