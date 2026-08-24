package com.tasklean.api.domain.device;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.device.dto.DeviceRequest;
import com.tasklean.api.domain.device.dto.DeviceResponse;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public DeviceResponse getDeviceById(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.DEVICE_NOT_FOUND));
        return DeviceResponse.from(device);
    }

    public List<DeviceResponse> getDevicesByUser(Long userId) {
        return deviceRepository.findByUserIdUserAndIsActiveTrue(userId).stream()
                .map(DeviceResponse::from)
                .toList();
    }

    @Transactional
    public DeviceResponse registerDevice(DeviceRequest request) {
        if (deviceRepository.existsByDeviceToken(request.getDeviceToken())) {
            throw new DuplicateResourceException("Device with this token already exists");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));

        Device device = Device.builder()
                .deviceToken(request.getDeviceToken())
                .deviceType(request.getDeviceType())
                .deviceName(request.getDeviceName())
                .browserInfo(request.getBrowserInfo())
                .osVersion(request.getOsVersion())
                .appVersion(request.getAppVersion())
                .isActive(true)
                .lastUsed(LocalDateTime.now(clock))
                .user(user)
                .build();
        return DeviceResponse.from(deviceRepository.save(device));
    }

    @Transactional
    public DeviceResponse updateDevice(Long id, DeviceRequest request) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.DEVICE_NOT_FOUND));
        device.setDeviceName(request.getDeviceName());
        device.setBrowserInfo(request.getBrowserInfo());
        device.setOsVersion(request.getOsVersion());
        device.setAppVersion(request.getAppVersion());
        device.setLastUsed(LocalDateTime.now(clock));
        return DeviceResponse.from(deviceRepository.save(device));
    }

    @Transactional
    public void deactivateDevice(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.DEVICE_NOT_FOUND));
        device.setIsActive(false);
        deviceRepository.save(device);
    }
}
