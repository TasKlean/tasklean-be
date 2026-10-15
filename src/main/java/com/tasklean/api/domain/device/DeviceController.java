package com.tasklean.api.domain.device;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.device.dto.DeviceResponse;
import com.tasklean.api.domain.device.dto.DeviceRequest;
import com.tasklean.api.domain.device.dto.MyDeviceResponse;
import com.tasklean.api.domain.device.dto.RenameDeviceRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The caller's own devices: the sign-in sessions behind them (list with session state, rename,
 * per-device logout, remove) and push-notification registration. Every endpoint acts on the
 * authenticated caller, resolved from the JWT principal — a user only ever sees or changes their
 * own devices.
 */
@Tag(name = "Devices", description = "The caller's own devices: list with session state, rename, per-device logout, remove, and push-notification registration.")
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    /**
     * Lists the caller's devices, flagging which have a live session and which is the current one.
     *
     * @param principal the authenticated caller
     * @return {@code 200 OK} with the caller's devices
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ApiResponse<List<MyDeviceResponse>>> getMyDevices(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(deviceService.listMyDevices(principal.userId())));
    }

    /**
     * Registers a push-notification token for the caller's current device.
     *
     * @param principal the authenticated caller
     * @param request   the push device details (token, type)
     * @return {@code 201 Created} with the registered device
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ApiResponse<DeviceResponse>> registerDevice(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody DeviceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(deviceService.registerDevice(principal.userId(), request)));
    }

    /**
     * Renames one of the caller's devices.
     *
     * @param principal the authenticated caller
     * @param id        the device id
     * @param request   the new display name
     * @return {@code 200 OK} with the updated device
     */
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<MyDeviceResponse>> renameDevice(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody RenameDeviceRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                deviceService.renameMyDevice(principal.userId(), id, request.getName())));
    }

    /**
     * Logs out a single device by revoking its session; the device stays listed as inactive.
     *
     * @param principal the authenticated caller
     * @param id        the device id
     * @return {@code 200 OK} with a confirmation message
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/logout")
    public ResponseEntity<ApiResponse<Void>> logoutDevice(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        deviceService.logoutMyDevice(principal.userId(), id);
        return ResponseEntity.ok(ApiResponse.success("Device logged out", null));
    }

    /**
     * Removes one of the caller's devices (soft-delete) and revokes any session on it.
     *
     * @param principal the authenticated caller
     * @param id        the device id
     * @return {@code 200 OK} with a confirmation message
     */
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> removeDevice(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        deviceService.deleteMyDevice(principal.userId(), id);
        return ResponseEntity.ok(ApiResponse.success("Device removed", null));
    }
}
