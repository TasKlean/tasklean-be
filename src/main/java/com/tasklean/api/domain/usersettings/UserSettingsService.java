package com.tasklean.api.domain.usersettings;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import com.tasklean.api.domain.usersettings.dto.UserSettingsRequest;
import com.tasklean.api.domain.usersettings.dto.UserSettingsResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Manages each user's UI settings (theme + language). The settings row is created lazily on the
 * first write — a read for a user who has none returns the defaults without persisting anything —
 * so existing accounts need no backfill.
 */
@Service
public class UserSettingsService {

    private final UserSettingsRepository userSettingsRepository;
    private final UserRepository userRepository;

    // A write runs through a template rather than @Transactional because losing the insert race
    // dooms the current transaction: the retry can only see the winner's row in a fresh one.
    private final TransactionTemplate transactionTemplate;

    public UserSettingsService(UserSettingsRepository userSettingsRepository,
                               UserRepository userRepository,
                               PlatformTransactionManager transactionManager) {
        this.userSettingsRepository = userSettingsRepository;
        this.userRepository = userRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Returns the caller's settings, or the defaults if they have never saved any.
     *
     * @param userId the authenticated caller's internal id
     * @return the caller's settings
     */
    @Transactional(readOnly = true)
    public UserSettingsResponse getMySettings(Long userId) {
        return userSettingsRepository.findByUserIdUser(userId)
                .map(UserSettingsResponse::from)
                .orElseGet(UserSettingsResponse::defaults);
    }

    /**
     * Replaces the caller's settings, creating the row on the first write. Two first-time writes
     * arriving together are safe: the one that loses the {@code unique(user_id)} race retries and
     * updates the row the winner created, so the caller never sees a constraint error.
     *
     * @param userId  the authenticated caller's internal id
     * @param request the new theme and locale
     * @return the saved settings
     * @throws ResourceNotFoundException if the user no longer exists
     */
    public UserSettingsResponse updateMySettings(Long userId, UserSettingsRequest request) {
        try {
            return transactionTemplate.execute(status -> applyUpdate(userId, request));
        } catch (DataIntegrityViolationException e) {
            // A concurrent first write committed the row first. Retrying in a clean transaction
            // now finds that row and updates it instead of inserting a duplicate.
            return transactionTemplate.execute(status -> applyUpdate(userId, request));
        }
    }

    private UserSettingsResponse applyUpdate(Long userId, UserSettingsRequest request) {
        UserSettings settings = userSettingsRepository.findByUserIdUser(userId)
                .orElseGet(() -> UserSettings.builder().user(requireUser(userId)).build());

        settings.setTheme(request.getTheme());
        settings.setLocale(request.getLocale());

        return UserSettingsResponse.from(userSettingsRepository.save(settings));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));
    }
}
