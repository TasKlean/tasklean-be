package com.tasklean.api.domain.device;

import com.tasklean.api.auth.refresh.RefreshTokenRepository;
import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.common.web.RequestContext;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.device.dto.DeviceRequest;
import com.tasklean.api.domain.device.dto.MyDeviceResponse;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private RequestContext requestContext;
    @Mock
    private AuditLogService auditLogService;
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
        return r;
    }

    private Device ownedDevice(Long id, String clientId) {
        return Device.builder().idDevice(id).clientDeviceId(clientId).deviceName("Chrome on Windows")
                .isActive(true).user(user()).build();
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

        assertThat(deviceService.registerDevice(USER_ID, request()).getDeviceToken()).isEqualTo("tok-1");
    }

    @Test
    void registerDevice_duplicateToken_throws() {
        when(deviceRepository.existsByDeviceToken("tok-1")).thenReturn(true);
        assertThatThrownBy(() -> deviceService.registerDevice(USER_ID, request()))
                .isInstanceOf(DuplicateResourceException.class);
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void registerDevice_userMissing_throws() {
        when(deviceRepository.existsByDeviceToken("tok-1")).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> deviceService.registerDevice(USER_ID, request()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- resolveLoginDevice ---

    @Test
    void resolveLoginDevice_noDeviceId_returnsEmptyOutcome() {
        when(requestContext.deviceId()).thenReturn(null);

        DeviceLoginOutcome outcome = deviceService.resolveLoginDevice(user());

        assertThat(outcome.device()).isNull();
        assertThat(outcome.isNew()).isFalse();
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void resolveLoginDevice_unseenDevice_createsAndFlagsNew() {
        when(requestContext.deviceId()).thenReturn("client-abc");
        when(requestContext.userAgent()).thenReturn(
                "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0 Safari/537.36");
        when(requestContext.clientIp()).thenReturn("203.0.113.5");
        when(deviceRepository.findByUserIdUserAndClientDeviceId(USER_ID, "client-abc"))
                .thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class))).thenAnswer(i -> i.getArgument(0));

        DeviceLoginOutcome outcome = deviceService.resolveLoginDevice(user());

        assertThat(outcome.isNew()).isTrue();
        assertThat(outcome.device().getClientDeviceId()).isEqualTo("client-abc");
        assertThat(outcome.device().getDeviceName()).isEqualTo("Chrome on Windows");
        assertThat(outcome.device().getIpAddress()).isEqualTo("203.0.113.5");
    }

    @Test
    void resolveLoginDevice_knownDevice_updatesAndKeepsName() {
        Device existing = ownedDevice(7L, "client-abc");
        existing.setDeviceName("My laptop"); // user-renamed; must survive re-login
        when(requestContext.deviceId()).thenReturn("client-abc");
        when(requestContext.userAgent()).thenReturn(
                "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0 Safari/537.36");
        when(requestContext.trustedClientIp()).thenReturn("203.0.113.9");
        when(deviceRepository.findByUserIdUserAndClientDeviceId(USER_ID, "client-abc"))
                .thenReturn(Optional.of(existing));
        when(deviceRepository.save(any(Device.class))).thenAnswer(i -> i.getArgument(0));

        DeviceLoginOutcome outcome = deviceService.resolveLoginDevice(user());

        assertThat(outcome.isNew()).isFalse();
        assertThat(outcome.device().getDeviceName()).isEqualTo("My laptop");
        assertThat(outcome.device().getIpAddress()).isEqualTo("203.0.113.9");
    }

    @Test
    void resolveLoginDevice_knownDeviceUntrustedHop_keepsStoredIp() {
        Device existing = ownedDevice(7L, "client-abc");
        existing.setIpAddress("203.0.113.9");
        when(requestContext.deviceId()).thenReturn("client-abc");
        when(requestContext.userAgent()).thenReturn(
                "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0 Safari/537.36");
        when(requestContext.trustedClientIp()).thenReturn(null);
        when(deviceRepository.findByUserIdUserAndClientDeviceId(USER_ID, "client-abc"))
                .thenReturn(Optional.of(existing));
        when(deviceRepository.save(any(Device.class))).thenAnswer(i -> i.getArgument(0));

        DeviceLoginOutcome outcome = deviceService.resolveLoginDevice(user());

        // Infrastructure's address must not replace the user's.
        assertThat(outcome.device().getIpAddress()).isEqualTo("203.0.113.9");
    }

    @Test
    void resolveLoginDevice_removedDeviceSignsInAgain_flagsNewAndResetsName() {
        Device removed = ownedDevice(7L, "client-abc");
        removed.setIsActive(false);
        removed.setDeviceName("My laptop");
        when(requestContext.deviceId()).thenReturn("client-abc");
        when(requestContext.userAgent()).thenReturn(
                "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0 Safari/537.36");
        when(deviceRepository.findByUserIdUserAndClientDeviceId(USER_ID, "client-abc"))
                .thenReturn(Optional.of(removed));
        when(deviceRepository.save(any(Device.class))).thenAnswer(i -> i.getArgument(0));

        DeviceLoginOutcome outcome = deviceService.resolveLoginDevice(user());

        // Flagged new so the caller alerts and audits the return of a device the user removed.
        assertThat(outcome.isNew()).isTrue();
        assertThat(outcome.device().getIsActive()).isTrue();
        assertThat(outcome.device().getDeviceName()).isEqualTo("Chrome on Windows");
    }

    // --- recordActivity ---

    @Test
    void recordActivity_trustedHop_updatesIpAndLastUsed() {
        Device device = ownedDevice(7L, "client-abc");
        device.setIpAddress("203.0.113.9");
        device.setLastUsed(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        when(requestContext.trustedClientIp()).thenReturn("198.51.100.4");

        deviceService.recordActivity(device);

        assertThat(device.getIpAddress()).isEqualTo("198.51.100.4");
        assertThat(device.getLastUsed()).isAfter(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        verify(deviceRepository).save(device);
    }

    @Test
    void recordActivity_untrustedHop_keepsIpButStillAdvancesLastUsed() {
        Device device = ownedDevice(7L, "client-abc");
        device.setIpAddress("203.0.113.9");
        device.setLastUsed(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        when(requestContext.trustedClientIp()).thenReturn(null);

        deviceService.recordActivity(device);

        assertThat(device.getIpAddress()).isEqualTo("203.0.113.9");
        assertThat(device.getLastUsed()).isAfter(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
    }

    // --- listMyDevices ---

    @Test
    void listMyDevices_flagsActiveSessionAndCurrentDevice() {
        Device current = ownedDevice(1L, "client-current");
        Device other = ownedDevice(2L, "client-other");
        when(refreshTokenRepository.findActiveDeviceIds(eq(USER_ID), any())).thenReturn(List.of(1L));
        when(requestContext.deviceId()).thenReturn("client-current");
        when(deviceRepository.findByUserIdUserAndIsActiveTrue(USER_ID))
                .thenReturn(List.of(current, other));

        List<MyDeviceResponse> result = deviceService.listMyDevices(USER_ID);

        MyDeviceResponse currentResponse = result.getFirst();
        assertThat(currentResponse.isActiveSession()).isTrue();
        assertThat(currentResponse.isCurrentDevice()).isTrue();
        MyDeviceResponse otherResponse = result.get(1);
        assertThat(otherResponse.isActiveSession()).isFalse();
        assertThat(otherResponse.isCurrentDevice()).isFalse();
    }

    // --- logoutMyDevice / deleteMyDevice / renameMyDevice ---

    @Test
    void logoutMyDevice_owned_revokesSessions() {
        when(deviceRepository.findById(7L)).thenReturn(Optional.of(ownedDevice(7L, "c")));

        deviceService.logoutMyDevice(USER_ID, 7L);

        verify(refreshTokenRepository).revokeAllByDeviceId(7L);
        verify(auditLogService).recordAccountEvent(eq(USER_ID), eq(AuditAction.SESSION_REVOKED), anyString());
    }

    @Test
    void logoutMyDevice_notOwned_throws() {
        Device someoneElses = Device.builder().idDevice(7L)
                .user(User.builder().idUser(999L).build()).build();
        when(deviceRepository.findById(7L)).thenReturn(Optional.of(someoneElses));

        assertThatThrownBy(() -> deviceService.logoutMyDevice(USER_ID, 7L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(refreshTokenRepository, never()).revokeAllByDeviceId(any());
    }

    @Test
    void deleteMyDevice_owned_softDeletesAndRevokes() {
        Device device = ownedDevice(7L, "c");
        when(deviceRepository.findById(7L)).thenReturn(Optional.of(device));

        deviceService.deleteMyDevice(USER_ID, 7L);

        assertThat(device.getIsActive()).isFalse();
        verify(deviceRepository).save(device);
        verify(refreshTokenRepository).revokeAllByDeviceId(7L);
        verify(auditLogService).recordAccountEvent(eq(USER_ID), eq(AuditAction.DEVICE_REMOVED), anyString());
    }

    @Test
    void renameMyDevice_owned_updatesName() {
        Device device = ownedDevice(7L, "c");
        when(deviceRepository.findById(7L)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(i -> i.getArgument(0));
        when(refreshTokenRepository.findActiveDeviceIds(eq(USER_ID), any())).thenReturn(List.of());

        MyDeviceResponse response = deviceService.renameMyDevice(USER_ID, 7L, "Work PC");

        assertThat(device.getDeviceName()).isEqualTo("Work PC");
        assertThat(response.getDeviceName()).isEqualTo("Work PC");
    }

    // --- deleteAllMyDevices ---

    @Test
    void deleteAllMyDevices_withDevices_deactivatesAllAndRevokesEverySession() {
        Device phone = ownedDevice(7L, "client-phone");
        Device laptop = ownedDevice(8L, "client-laptop");
        when(deviceRepository.findByUserIdUserAndIsActiveTrue(USER_ID)).thenReturn(List.of(phone, laptop));

        deviceService.deleteAllMyDevices(USER_ID);

        assertThat(phone.getIsActive()).isFalse();
        assertThat(laptop.getIsActive()).isFalse();
        verify(deviceRepository).saveAll(List.of(phone, laptop));
        verify(refreshTokenRepository).revokeAllByUserId(USER_ID);
        verify(auditLogService)
                .recordAccountEvent(eq(USER_ID), eq(AuditAction.ALL_DEVICES_REMOVED), anyString());
    }

    @Test
    void deleteAllMyDevices_noDevices_stillRevokesEverySession() {
        when(deviceRepository.findByUserIdUserAndIsActiveTrue(USER_ID)).thenReturn(List.of());

        deviceService.deleteAllMyDevices(USER_ID);

        // A session can outlive its device row (a legacy token has none), so the revoke is not
        // conditional on there being devices to remove.
        verify(refreshTokenRepository).revokeAllByUserId(USER_ID);
    }

    @Test
    void deleteAllMyDevices_oneDevice_revokesByUserNotByDevice() {
        Device only = ownedDevice(7L, "client-phone");
        when(deviceRepository.findByUserIdUserAndIsActiveTrue(USER_ID)).thenReturn(List.of(only));

        deviceService.deleteAllMyDevices(USER_ID);

        verify(refreshTokenRepository, never()).revokeAllByDeviceId(any());
    }
}
