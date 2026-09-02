package com.tasklean.api.domain.device;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.device.dto.DeviceRequest;
import com.tasklean.api.domain.device.dto.DeviceResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for user devices — register, fetch, list by user, update, and deactivate.
 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    /**
     * Registers a device for push notifications.
     *
     * @param request the device details (token, type, user)
     * @return {@code 201 Created} with the registered device
     */
    @PostMapping
    public ResponseEntity<ApiResponse<DeviceResponse>> registerDevice(@Valid @RequestBody DeviceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(deviceService.registerDevice(request)));
    }

    /**
     * Fetches a device by id.
     *
     * @param id the device id
     * @return {@code 200 OK} with the device
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DeviceResponse>> getDevice(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(deviceService.getDeviceById(id)));
    }

    /**
     * Lists a user's active devices.
     *
     * @param userId the user id
     * @return {@code 200 OK} with the devices
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> getDevicesByUser(@RequestParam Long userId) {
        return ResponseEntity.ok(ApiResponse.success(deviceService.getDevicesByUser(userId)));
    }

    /**
     * Updates a device's descriptive fields.
     *
     * @param id      the device id
     * @param request the new device details
     * @return {@code 200 OK} with the updated device
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DeviceResponse>> updateDevice(
            @PathVariable Long id, @Valid @RequestBody DeviceRequest request) {
        return ResponseEntity.ok(ApiResponse.success(deviceService.updateDevice(id, request)));
    }

    /**
     * Soft-deletes (deactivates) a device.
     *
     * @param id the device id
     * @return {@code 200 OK} with a confirmation message
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateDevice(@PathVariable Long id) {
        deviceService.deactivateDevice(id);
        return ResponseEntity.ok(ApiResponse.success("Device deactivated", null));
    }
}
