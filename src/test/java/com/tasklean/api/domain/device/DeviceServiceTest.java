package com.tasklean.api.domain.device;

import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.device.dto.DeviceRequest;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private UserRepository userRepository;
    @Spy
    private Clock clock = Clock.systemUTC();
    @InjectMocks
    private DeviceService deviceService;

    private static final Long USER_ID = 1L;

    private User user() {
        return User.builder().idUser(USER_ID).build();
    }

    private DeviceRequest request() {
        DeviceRequest r = new DeviceRequest();
        r.setDeviceToken("tok-1");
        r.setDeviceType("ANDROID");
        r.setDeviceName("Pixel");
        r.setUserId(USER_ID);
        return r;
    }

    private Device device() {
        return Device.builder().idDevice(1L).deviceToken("tok-1").deviceType("ANDROID")
                .isActive(true).user(user()).build();
    }

    @Test
    void getDeviceById_found_returns() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device()));
        assertThat(deviceService.getDeviceById(1L).getId()).isEqualTo(1L);
    }

    @Test
    void getDeviceById_missing_throws() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> deviceService.getDeviceById(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getDevicesByUser_returnsList() {
        when(deviceRepository.findByUserIdUserAndIsActiveTrue(USER_ID)).thenReturn(List.of(device()));
        assertThat(deviceService.getDevicesByUser(USER_ID)).hasSize(1);
    }

    @Test
    void registerDevice_success() {
        when(deviceRepository.existsByDeviceToken("tok-1")).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(deviceRepository.save(any(Device.class))).thenAnswer(i -> {
            Device d = i.getArgument(0);
            d.setIdDevice(1L);
            return d;
        });

        assertThat(deviceService.registerDevice(request()).getDeviceToken()).isEqualTo("tok-1");
    }

    @Test
    void registerDevice_duplicateToken_throws() {
        when(deviceRepository.existsByDeviceToken("tok-1")).thenReturn(true);
        assertThatThrownBy(() -> deviceService.registerDevice(request())).isInstanceOf(DuplicateResourceException.class);
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void registerDevice_userMissing_throws() {
        when(deviceRepository.existsByDeviceToken("tok-1")).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> deviceService.registerDevice(request())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateDevice_success() {
        Device existing = device();
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(deviceRepository.save(any(Device.class))).thenAnswer(i -> i.getArgument(0));

        deviceService.updateDevice(1L, request());

        assertThat(existing.getDeviceName()).isEqualTo("Pixel");
    }

    @Test
    void updateDevice_missing_throws() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> deviceService.updateDevice(1L, request())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivateDevice_success() {
        Device existing = device();
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(existing));

        deviceService.deactivateDevice(1L);

        assertThat(existing.getIsActive()).isFalse();
    }

    @Test
    void deactivateDevice_missing_throws() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> deviceService.deactivateDevice(1L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
