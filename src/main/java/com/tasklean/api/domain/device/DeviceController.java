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

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceResponse>> registerDevice(@Valid @RequestBody DeviceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(deviceService.registerDevice(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DeviceResponse>> getDevice(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(deviceService.getDeviceById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> getDevicesByUser(@RequestParam Long userId) {
        return ResponseEntity.ok(ApiResponse.success(deviceService.getDevicesByUser(userId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DeviceResponse>> updateDevice(
            @PathVariable Long id, @Valid @RequestBody DeviceRequest request) {
        return ResponseEntity.ok(ApiResponse.success(deviceService.updateDevice(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateDevice(@PathVariable Long id) {
        deviceService.deactivateDevice(id);
        return ResponseEntity.ok(ApiResponse.success("Device deactivated", null));
    }
}
