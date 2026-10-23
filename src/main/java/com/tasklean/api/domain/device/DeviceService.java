package com.tasklean.api.domain.device;

import com.tasklean.api.auth.refresh.RefreshTokenRepository;
import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.common.web.RequestContext;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.device.dto.DeviceRequest;
import com.tasklean.api.domain.device.dto.DeviceResponse;
import com.tasklean.api.domain.device.dto.MyDeviceResponse;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Manages user devices. Covers both roles the device table now plays: push-notification
 * registration (token-keyed CRUD) and the durable per-device record behind sign-in sessions —
 * resolving the device at login and the owner-scoped listing, renaming, per-device logout and
 * removal used by account settings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RequestContext requestContext;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /**
     * Registers a push-notification token for the caller, so the device can receive pushes.
     *
     * @param userId  the caller's user id
     * @param request the push device details (token, type, descriptive fields)
     * @return the registered device
     * @throws DuplicateResourceException if the device token is already registered
     * @throws ResourceNotFoundException  if the user does not exist
     */
    @Transactional
    public DeviceResponse registerDevice(Long userId, DeviceRequest request) {
        if (deviceRepository.existsByDeviceToken(request.getDeviceToken())) {
            throw new DuplicateResourceException("Device with this token already exists");
        }

        User user = userRepository.findById(userId)
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

    // --- Sign-in session support ---

    /**
     * Resolves the device a sign-in runs on, from the client device id, user-agent and IP on the
     * current request. Finds the user's existing device by its client id, or creates one if unseen;
     * refreshes its last-used and browser details either way, and its IP only when a trusted hop
     * vouched for it. A login with no device id yields an empty outcome (no device is created, so
     * the session simply has none).
     *
     * @param user the user signing in
     * @return the resolved device and whether it was first seen on this login
     */
    @Transactional
    public DeviceLoginOutcome resolveLoginDevice(User user) {
        String clientDeviceId = requestContext.deviceId();
        if (clientDeviceId == null || clientDeviceId.isBlank()) {
            return new DeviceLoginOutcome(null, false);
        }

        String userAgent = requestContext.userAgent();
        DeviceInfo info = UserAgentParser.parse(userAgent);
        LocalDateTime now = LocalDateTime.now(clock);

        Device device = deviceRepository
                .findByUserIdUserAndClientDeviceId(user.getIdUser(), clientDeviceId)
                .orElse(null);
        boolean isNew = device == null;

        if (isNew) {
            device = Device.builder()
                    .clientDeviceId(clientDeviceId)
                    .deviceType(info.type())
                    .deviceName(info.displayName())
                    .browserInfo(info.browser())
                    .osVersion(info.os())
                    .userAgent(userAgent)
                    // Unguarded, unlike the update below: a new row has no better value to keep,
                    // and the new-sign-in email reads this.
                    .ipAddress(requestContext.clientIp())
                    .lastUsed(now)
                    .isActive(true)
                    .user(user)
                    .build();
        } else {
            // Keep deviceName as-is so a user rename survives; refresh the technical details.
            device.setBrowserInfo(info.browser());
            device.setOsVersion(info.os());
            device.setDeviceType(info.type());
            device.setUserAgent(userAgent);
            updateIpIfKnown(device);
            device.setLastUsed(now);
            device.setIsActive(true);
        }

        Device saved = deviceRepository.save(device);
        return new DeviceLoginOutcome(saved, isNew);
    }

    /**
     * Records that a session was used: the last-used time always, and the IP when the request
     * proves where the end user is. The single writer of device activity, so the timestamp and
     * the address cannot drift apart again.
     *
     * @param device the device the refreshed session belongs to
     */
    @Transactional
    public void recordActivity(Device device) {
        device.setLastUsed(LocalDateTime.now(clock));
        updateIpIfKnown(device);
        deviceRepository.save(device);
    }

    /**
     * Lists the caller's devices for account settings, flagging which have a live session and which
     * is the device making the current request.
     *
     * @param userId the caller's user id
     * @return the caller's active devices as session-view responses
     */
    public List<MyDeviceResponse> listMyDevices(Long userId) {
        Set<Long> activeDeviceIds = Set.copyOf(
                refreshTokenRepository.findActiveDeviceIds(userId, LocalDateTime.now(clock)));
        String currentClientId = requestContext.deviceId();

        return deviceRepository.findByUserIdUserAndIsActiveTrue(userId).stream()
                .map(device -> MyDeviceResponse.from(device,
                        activeDeviceIds.contains(device.getIdDevice()),
                        currentClientId != null && currentClientId.equals(device.getClientDeviceId())))
                .toList();
    }

    /**
     * Renames one of the caller's devices.
     *
     * @param userId the caller's user id
     * @param id     the device id
     * @param name   the new display name
     * @return the updated device
     * @throws ResourceNotFoundException if the device doesn't exist or isn't the caller's
     */
    @Transactional
    public MyDeviceResponse renameMyDevice(Long userId, Long id, String name) {
        Device device = getOwnedDevice(userId, id);
        device.setDeviceName(name);
        Device saved = deviceRepository.save(device);

        boolean active = refreshTokenRepository
                .findActiveDeviceIds(userId, LocalDateTime.now(clock))
                .contains(saved.getIdDevice());
        String currentClientId = requestContext.deviceId();
        return MyDeviceResponse.from(saved, active,
                currentClientId != null && currentClientId.equals(saved.getClientDeviceId()));
    }

    /**
     * Ends the session on one of the caller's devices by revoking its refresh tokens. The device
     * row stays (it shows as inactive).
     *
     * @param userId the caller's user id
     * @param id     the device id
     * @throws ResourceNotFoundException if the device doesn't exist or isn't the caller's
     */
    @Transactional
    public void logoutMyDevice(Long userId, Long id) {
        Device device = getOwnedDevice(userId, id);
        refreshTokenRepository.revokeAllByDeviceId(device.getIdDevice());
        log.info("Session revoked for device: userId={} deviceId={}", userId, id);
        auditLogService.recordAccountEvent(userId, AuditAction.SESSION_REVOKED,
                "Session revoked for device: " + device.getDeviceName());
    }

    /**
     * Removes one of the caller's devices: soft-deletes it and revokes any session on it. Used when
     * the device is no longer the user's.
     *
     * @param userId the caller's user id
     * @param id     the device id
     * @throws ResourceNotFoundException if the device doesn't exist or isn't the caller's
     */
    @Transactional
    public void deleteMyDevice(Long userId, Long id) {
        Device device = getOwnedDevice(userId, id);
        device.setIsActive(false);
        deviceRepository.save(device);
        refreshTokenRepository.revokeAllByDeviceId(device.getIdDevice());
        log.info("Device removed: userId={} deviceId={}", userId, id);
        auditLogService.recordAccountEvent(userId, AuditAction.DEVICE_REMOVED,
                "Device removed: " + device.getDeviceName());
    }

    // Only a trusted hop carries the user's own address; an untrusted one resolves to our
    // frontend's egress IP. Widen this when a native client - untrusted but honest - ships.
    private void updateIpIfKnown(Device device) {
        String ip = requestContext.trustedClientIp();
        if (ip != null) {
            device.setIpAddress(ip);
        }
    }

    // Resolves a device that must belong to the caller. A non-owned or missing id yields the same
    // "not found" so a caller can't probe other users' device ids.
    private Device getOwnedDevice(Long userId, Long id) {
        return deviceRepository.findById(id)
                .filter(device -> device.getUser().getIdUser().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.DEVICE_NOT_FOUND));
    }
}
