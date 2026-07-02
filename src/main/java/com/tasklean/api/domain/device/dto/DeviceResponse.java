package com.tasklean.api.domain.device.dto;

import com.tasklean.api.domain.device.Device;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceResponse {

    private Long id;
    private String deviceToken;
    private String deviceType;
    private String deviceName;
    private String browserInfo;
    private String osVersion;
    private String appVersion;
    private Boolean isActive;
    private LocalDateTime lastUsed;
    private Long userId;
    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;

    public static DeviceResponse from(Device device) {
        return DeviceResponse.builder()
                .id(device.getIdDevice())
                .deviceToken(device.getDeviceToken())
                .deviceType(device.getDeviceType())
                .deviceName(device.getDeviceName())
                .browserInfo(device.getBrowserInfo())
                .osVersion(device.getOsVersion())
                .appVersion(device.getAppVersion())
                .isActive(device.getIsActive())
                .lastUsed(device.getLastUsed())
                .userId(device.getUser().getIdUser())
                .dateCreated(device.getDateCreated())
                .dateUpdated(device.getDateUpdated())
                .build();
    }
}
