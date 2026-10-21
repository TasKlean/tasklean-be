package com.tasklean.api.domain.device.dto;

import com.tasklean.api.domain.device.Device;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyDeviceResponse {

    private Long id;
    private String deviceName;
    private String deviceType;
    private String browserInfo;
    private String osVersion;
    private String ipAddress;
    private LocalDateTime lastUsed;
    // Whether this device currently has a live (non-revoked, unexpired) refresh token.
    private boolean activeSession;
    // Whether this is the device making the current request.
    private boolean currentDevice;
    private LocalDateTime dateCreated;

    public static MyDeviceResponse from(Device device, boolean activeSession, boolean currentDevice) {
        return MyDeviceResponse.builder()
                .id(device.getIdDevice())
                .deviceName(device.getDeviceName())
                .deviceType(device.getDeviceType())
                .browserInfo(device.getBrowserInfo())
                .osVersion(device.getOsVersion())
                .ipAddress(device.getIpAddress())
                .lastUsed(device.getLastUsed())
                .activeSession(activeSession)
                .currentDevice(currentDevice)
                .dateCreated(device.getDateCreated())
                .build();
    }
}
