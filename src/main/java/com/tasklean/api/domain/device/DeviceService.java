package com.tasklean.api.domain.device;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.device.dto.DeviceRequest;
import com.tasklean.api.domain.device.dto.DeviceResponse;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Manages user devices for push notifications — registration, lookup, listing,
 * updates, and deactivation. Device tokens are unique.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * Returns a device by its id.
     *
     * @param id the device id
     * @return the device
     * @throws ResourceNotFoundException if no device has that id
     */
    public DeviceResponse getDeviceById(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.DEVICE_NOT_FOUND));
        return DeviceResponse.from(device);
    }

    /**
     * Returns a user's active devices.
     *
     * @param userId the user id
     * @return the user's active devices
     */
    public List<DeviceResponse> getDevicesByUser(Long userId) {
        return deviceRepository.findByUserIdUserAndIsActiveTrue(userId).stream()
                .map(DeviceResponse::from)
                .toList();
    }

    /**
     * Registers a device for a user.
     *
     * @param request the device details (token, type, user)
     * @return the registered device
     * @throws DuplicateResourceException if the device token is already registered
     * @throws ResourceNotFoundException  if the user does not exist
     */
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
        Device saved = deviceRepository.save(device);
        log.info("Device registered: id={} user={}", saved.getIdDevice(), user.getIdUser());
        return DeviceResponse.from(saved);
    }

    /**
     * Updates a device's descriptive fields and last-used timestamp.
     *
     * @param id      the device id
     * @param request the new device details
     * @return the updated device
     * @throws ResourceNotFoundException if no device has that id
     */
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

    /**
     * Soft-deletes a device (sets it inactive).
     *
     * @param id the device id
     * @throws ResourceNotFoundException if no device has that id
     */
    @Transactional
    public void deactivateDevice(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.DEVICE_NOT_FOUND));
        device.setIsActive(false);
        deviceRepository.save(device);
        log.info("Device deactivated: id={}", id);
    }
}
