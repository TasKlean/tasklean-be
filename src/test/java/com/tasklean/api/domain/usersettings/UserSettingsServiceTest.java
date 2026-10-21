package com.tasklean.api.domain.usersettings;

import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import com.tasklean.api.domain.usersettings.dto.UserSettingsRequest;
import com.tasklean.api.domain.usersettings.dto.UserSettingsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTest {

    @Mock
    private UserSettingsRepository userSettingsRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlatformTransactionManager transactionManager;
    @InjectMocks
    private UserSettingsService userSettingsService;

    private static final Long USER_ID = 1L;

    private UserSettings saved(Theme theme, AppLocale locale) {
        return UserSettings.builder().idUserSettings(5L)
                .user(User.builder().idUser(USER_ID).build())
                .theme(theme).locale(locale).build();
    }

    // --- getMySettings ---

    @Test
    void getMySettings_existingRow_returnsSavedValues() {
        when(userSettingsRepository.findByUserIdUser(USER_ID))
                .thenReturn(Optional.of(saved(Theme.DARK, AppLocale.SL)));

        UserSettingsResponse response = userSettingsService.getMySettings(USER_ID);

        assertThat(response.theme()).isEqualTo(Theme.DARK);
        assertThat(response.locale()).isEqualTo(AppLocale.SL);
    }

    @Test
    void getMySettings_noRow_returnsDefaultsWithoutWriting() {
        when(userSettingsRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.empty());

        UserSettingsResponse response = userSettingsService.getMySettings(USER_ID);

        assertThat(response.theme()).isEqualTo(Theme.SYSTEM);
        assertThat(response.locale()).isEqualTo(AppLocale.EN);
        // A read must never create the row (lazy-on-write).
        verify(userSettingsRepository, never()).save(any());
    }

    // --- updateMySettings ---

    @Test
    void updateMySettings_noRow_createsRowForUser() {
        when(userSettingsRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.empty());
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(User.builder().idUser(USER_ID).build()));
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(i -> i.getArgument(0));

        UserSettingsResponse response = userSettingsService.updateMySettings(USER_ID,
                new UserSettingsRequest(Theme.LIGHT, AppLocale.DE));

        ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
        verify(userSettingsRepository).save(captor.capture());
        UserSettings created = captor.getValue();
        assertThat(created.getUser().getIdUser()).isEqualTo(USER_ID);
        assertThat(created.getTheme()).isEqualTo(Theme.LIGHT);
        assertThat(created.getLocale()).isEqualTo(AppLocale.DE);
        assertThat(response.locale()).isEqualTo(AppLocale.DE);
    }

    @Test
    void updateMySettings_existingRow_updatesInPlace() {
        UserSettings existing = saved(Theme.SYSTEM, AppLocale.EN);
        when(userSettingsRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.of(existing));
        when(userSettingsRepository.save(existing)).thenReturn(existing);

        UserSettingsResponse response = userSettingsService.updateMySettings(USER_ID,
                new UserSettingsRequest(Theme.DARK, AppLocale.SL));

        assertThat(existing.getTheme()).isEqualTo(Theme.DARK);
        assertThat(existing.getLocale()).isEqualTo(AppLocale.SL);
        assertThat(response.theme()).isEqualTo(Theme.DARK);
        // Reuses the existing row rather than creating a second one.
        verify(userRepository, never()).findById(any());
    }

    @Test
    void updateMySettings_concurrentFirstWrite_retriesAndUpdatesTheWinnersRow() {
        UserSettings winnersRow = saved(Theme.SYSTEM, AppLocale.EN);
        // First attempt sees no row and loses the unique(user_id) race; the retry finds the row
        // the concurrent write committed.
        when(userSettingsRepository.findByUserIdUser(USER_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnersRow));
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(User.builder().idUser(USER_ID).build()));
        when(userSettingsRepository.save(any(UserSettings.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"))
                .thenAnswer(i -> i.getArgument(0));

        UserSettingsResponse response = userSettingsService.updateMySettings(USER_ID,
                new UserSettingsRequest(Theme.DARK, AppLocale.DE));

        // The caller gets a success, not a constraint error, and the values land on the kept row.
        assertThat(response.theme()).isEqualTo(Theme.DARK);
        assertThat(response.locale()).isEqualTo(AppLocale.DE);
        assertThat(winnersRow.getTheme()).isEqualTo(Theme.DARK);
        verify(userSettingsRepository, times(2)).save(any(UserSettings.class));
        // The retry must run in its own transaction - the failed insert dooms the first one.
        verify(transactionManager, times(2)).getTransaction(any());
    }

    @Test
    void updateMySettings_unknownUser_throwsNotFound() {
        when(userSettingsRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.empty());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userSettingsService.updateMySettings(USER_ID,
                new UserSettingsRequest(Theme.DARK, AppLocale.SL)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userSettingsRepository, never()).save(any());
    }
}
