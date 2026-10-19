package com.tasklean.api.domain.notificationsettings;

import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.notification.NotificationType;
import com.tasklean.api.domain.notificationsettings.dto.NotificationSettingEntry;
import com.tasklean.api.domain.notificationsettings.dto.NotificationSettingRequest;
import com.tasklean.api.domain.notificationsettings.dto.NotificationSettingResponse;
import com.tasklean.api.domain.notificationsettings.dto.NotificationSettingsBatchRequest;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSettingsServiceTest {

    @Mock
    private NotificationSettingsRepository notificationSettingsRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlatformTransactionManager transactionManager;
    @InjectMocks
    private NotificationSettingsService notificationSettingsService;

    private static final Long USER_ID = 1L;

    private NotificationSettings saved(NotificationType type, boolean inApp, boolean email) {
        return NotificationSettings.builder().idNotificationSettings(9L)
                .user(User.builder().idUser(USER_ID).build())
                .type(type).inApp(inApp).email(email).build();
    }

    // --- getMySettings ---

    @Test
    void getMySettings_noRows_returnsEveryTypeAtDefaults() {
        when(notificationSettingsRepository.findByUserIdUser(USER_ID)).thenReturn(List.of());

        List<NotificationSettingResponse> result = notificationSettingsService.getMySettings(USER_ID);

        // One entry per known type, so a client never needs to know the defaults.
        assertThat(result).hasSize(NotificationType.values().length);
        assertThat(result).allSatisfy(entry -> {
            assertThat(entry.inApp()).isTrue();
            assertThat(entry.email()).isTrue();
        });
        verify(notificationSettingsRepository, never()).save(any());
    }

    @Test
    void getMySettings_someRowsSaved_mergesSavedOverDefaults() {
        when(notificationSettingsRepository.findByUserIdUser(USER_ID))
                .thenReturn(List.of(saved(NotificationType.TASK_COMPLETED, false, false)));

        List<NotificationSettingResponse> result = notificationSettingsService.getMySettings(USER_ID);

        NotificationSettingResponse completed = result.stream()
                .filter(e -> e.type() == NotificationType.TASK_COMPLETED).findFirst().orElseThrow();
        assertThat(completed.inApp()).isFalse();
        assertThat(completed.email()).isFalse();

        // Types with no row still read as the defaults.
        NotificationSettingResponse assigned = result.stream()
                .filter(e -> e.type() == NotificationType.TASK_ASSIGNED).findFirst().orElseThrow();
        assertThat(assigned.inApp()).isTrue();
        assertThat(assigned.email()).isTrue();
    }

    @Test
    void getMySettings_returnsTypesInDeclarationOrder() {
        when(notificationSettingsRepository.findByUserIdUser(USER_ID)).thenReturn(List.of());

        List<NotificationSettingResponse> result = notificationSettingsService.getMySettings(USER_ID);

        assertThat(result.stream().map(NotificationSettingResponse::type))
                .containsExactly(NotificationType.values());
    }

    // --- updateMySetting ---

    @Test
    void updateMySetting_noRow_createsRowForUserAndType() {
        when(notificationSettingsRepository.findByUserIdUserAndType(USER_ID, NotificationType.TASK_REMINDER))
                .thenReturn(Optional.empty());
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(User.builder().idUser(USER_ID).build()));
        when(notificationSettingsRepository.save(any(NotificationSettings.class)))
                .thenAnswer(i -> i.getArgument(0));

        NotificationSettingResponse response = notificationSettingsService.updateMySetting(
                USER_ID, NotificationType.TASK_REMINDER, new NotificationSettingRequest(true, false));

        ArgumentCaptor<NotificationSettings> captor = ArgumentCaptor.forClass(NotificationSettings.class);
        verify(notificationSettingsRepository).save(captor.capture());
        NotificationSettings created = captor.getValue();
        assertThat(created.getUser().getIdUser()).isEqualTo(USER_ID);
        assertThat(created.getType()).isEqualTo(NotificationType.TASK_REMINDER);
        assertThat(created.getEmail()).isFalse();
        assertThat(response.email()).isFalse();
    }

    @Test
    void updateMySetting_existingRow_updatesInPlace() {
        NotificationSettings existing = saved(NotificationType.MEMBER_JOINED, true, true);
        when(notificationSettingsRepository.findByUserIdUserAndType(USER_ID, NotificationType.MEMBER_JOINED))
                .thenReturn(Optional.of(existing));
        when(notificationSettingsRepository.save(existing)).thenReturn(existing);

        NotificationSettingResponse response = notificationSettingsService.updateMySetting(
                USER_ID, NotificationType.MEMBER_JOINED, new NotificationSettingRequest(false, true));

        assertThat(existing.getInApp()).isFalse();
        assertThat(response.inApp()).isFalse();
        // Reuses the existing row rather than resolving the user again.
        verify(userRepository, never()).findById(any());
    }

    @Test
    void updateMySetting_concurrentFirstWrite_retriesAndUpdatesTheWinnersRow() {
        NotificationSettings winnersRow = saved(NotificationType.TASK_ASSIGNED, true, true);
        when(notificationSettingsRepository.findByUserIdUserAndType(USER_ID, NotificationType.TASK_ASSIGNED))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnersRow));
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(User.builder().idUser(USER_ID).build()));
        when(notificationSettingsRepository.save(any(NotificationSettings.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"))
                .thenAnswer(i -> i.getArgument(0));

        NotificationSettingResponse response = notificationSettingsService.updateMySetting(
                USER_ID, NotificationType.TASK_ASSIGNED, new NotificationSettingRequest(false, false));

        // The caller gets a success, not a constraint error.
        assertThat(response.inApp()).isFalse();
        assertThat(winnersRow.getInApp()).isFalse();
        verify(notificationSettingsRepository, times(2)).save(any(NotificationSettings.class));
        // The retry must run in its own transaction - the failed insert dooms the first one.
        verify(transactionManager, times(2)).getTransaction(any());
    }

    @Test
    void updateMySetting_unknownUser_throwsNotFound() {
        when(notificationSettingsRepository.findByUserIdUserAndType(USER_ID, NotificationType.TASK_ASSIGNED))
                .thenReturn(Optional.empty());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationSettingsService.updateMySetting(
                USER_ID, NotificationType.TASK_ASSIGNED, new NotificationSettingRequest(true, true)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(notificationSettingsRepository, never()).save(any());
    }

    // --- updateMySettings (batch) ---

    private NotificationSettingsBatchRequest batchOf(NotificationSettingEntry... entries) {
        return NotificationSettingsBatchRequest.builder().settings(List.of(entries)).build();
    }

    private NotificationSettingEntry entry(NotificationType type, boolean inApp, boolean email) {
        return NotificationSettingEntry.builder().type(type).inApp(inApp).email(email).build();
    }

    @Test
    void updateMySettings_batch_appliesEveryEntryAndReturnsTheWholeMatrix() {
        NotificationSettings assignedRow = saved(NotificationType.TASK_ASSIGNED, true, true);
        NotificationSettings reminderRow = saved(NotificationType.TASK_REMINDER, true, true);
        when(notificationSettingsRepository.findByUserIdUser(USER_ID))
                .thenReturn(List.of(assignedRow, reminderRow));
        when(notificationSettingsRepository.save(any(NotificationSettings.class)))
                .thenAnswer(i -> i.getArgument(0));

        List<NotificationSettingResponse> result = notificationSettingsService.updateMySettings(USER_ID,
                batchOf(entry(NotificationType.TASK_ASSIGNED, false, false),
                        entry(NotificationType.TASK_REMINDER, true, false)));

        assertThat(assignedRow.getInApp()).isFalse();
        assertThat(reminderRow.getEmail()).isFalse();
        // The response covers every type, not just the two the batch touched.
        assertThat(result).hasSize(NotificationType.values().length);
        verify(notificationSettingsRepository, times(2)).save(any(NotificationSettings.class));
    }

    @Test
    void updateMySettings_batch_mixesCreateAndUpdateInOneTransaction() {
        when(notificationSettingsRepository.findByUserIdUser(USER_ID))
                .thenReturn(List.of(saved(NotificationType.TASK_ASSIGNED, true, true)));
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(User.builder().idUser(USER_ID).build()));
        when(notificationSettingsRepository.save(any(NotificationSettings.class)))
                .thenAnswer(i -> i.getArgument(0));

        notificationSettingsService.updateMySettings(USER_ID,
                batchOf(entry(NotificationType.TASK_ASSIGNED, false, true),
                        entry(NotificationType.MEMBER_JOINED, false, false)));

        verify(notificationSettingsRepository, times(2)).save(any(NotificationSettings.class));
        // One transaction for the whole batch, not one per entry.
        verify(transactionManager, times(1)).getTransaction(any());
    }

    @Test
    void updateMySettings_batch_concurrentFirstWrite_retriesTheWholeBatch() {
        when(notificationSettingsRepository.findByUserIdUser(USER_ID)).thenReturn(List.of());
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(User.builder().idUser(USER_ID).build()));
        when(notificationSettingsRepository.save(any(NotificationSettings.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"))
                .thenAnswer(i -> i.getArgument(0));

        List<NotificationSettingResponse> result = notificationSettingsService.updateMySettings(USER_ID,
                batchOf(entry(NotificationType.TASK_COMPLETED, false, false)));

        assertThat(result).hasSize(NotificationType.values().length);
        verify(transactionManager, times(2)).getTransaction(any());
    }

    @Test
    void updateMySettings_batch_unknownUser_throwsNotFound() {
        when(notificationSettingsRepository.findByUserIdUser(USER_ID)).thenReturn(List.of());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationSettingsService.updateMySettings(USER_ID,
                batchOf(entry(NotificationType.TASK_ASSIGNED, true, true))))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(notificationSettingsRepository, never()).save(any());
    }
}
