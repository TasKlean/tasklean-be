package com.tasklean.api.domain.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRequest {

    @NotBlank
    private String deviceToken;

    @NotBlank
    private String deviceType;

    private String deviceName;

    private String browserInfo;

    private String osVersion;

    private String appVersion;

    @NotNull
    private Long userId;
}
