package com.tasklean.api.domain.notificationsettings;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.notification.NotificationType;
import com.tasklean.api.domain.notificationsettings.dto.NotificationSettingEntry;
import com.tasklean.api.domain.notificationsettings.dto.NotificationSettingRequest;
import com.tasklean.api.domain.notificationsettings.dto.NotificationSettingResponse;
import com.tasklean.api.domain.notificationsettings.dto.NotificationSettingsBatchRequest;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Manages each user's notification settings — which channels deliver which notification type.
 * A row is created lazily per type on the first write, so a user with no rows reads as "enabled
 * everywhere" and existing accounts need no backfill.
 */
@Service
public class NotificationSettingsService {

    private final NotificationSettingsRepository notificationSettingsRepository;
    private final UserRepository userRepository;

    // A write runs through a template rather than @Transactional because losing the
    // unique(user_id, type) race dooms the current transaction: the retry needs a fresh one.
    private final TransactionTemplate transactionTemplate;

    public NotificationSettingsService(NotificationSettingsRepository notificationSettingsRepository,
                                       UserRepository userRepository,
                                       PlatformTransactionManager transactionManager) {
        this.notificationSettingsRepository = notificationSettingsRepository;
        this.userRepository = userRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Returns the caller's settings for every notification type, falling back to the defaults for
     * any type they have never saved. Always returns one entry per known type, so a client never
     * has to know the defaults itself.
     *
     * @param userId the authenticated caller's internal id
     * @return one entry per notification type, in declaration order
     */
    @Transactional(readOnly = true)
    public List<NotificationSettingResponse> getMySettings(Long userId) {
        return effectiveMatrix(userId);
    }

    /**
     * Replaces the caller's channels for one notification type, creating the row on the first
     * write. Two first-time writes for the same type arriving together are safe: the one that
     * loses the unique-constraint race retries and updates the row the winner created.
     *
     * @param userId  the authenticated caller's internal id
     * @param type    the notification type to configure
     * @param request the channel preferences
     * @return the saved entry
     * @throws ResourceNotFoundException if the user no longer exists
     */
    public NotificationSettingResponse updateMySetting(Long userId, NotificationType type,
                                                       NotificationSettingRequest request) {
        return writeWithRetry(() -> applyUpdate(userId, type, request));
    }

    /**
     * Applies a batch of entries in one transaction — each entry fully replaces that type's
     * channels, and types the batch omits are left untouched. Returns the caller's whole
     * effective matrix read back after the writes, so the client never has to infer the result.
     *
     * @param userId  the authenticated caller's internal id
     * @param request the entries to apply
     * @return one entry per notification type, in declaration order
     * @throws ResourceNotFoundException if the user no longer exists
     */
    public List<NotificationSettingResponse> updateMySettings(Long userId,
                                                              NotificationSettingsBatchRequest request) {
        return writeWithRetry(() -> applyBatch(userId, request));
    }

    // Both write paths share the same first-write race: the loser's transaction is doomed, so the
    // retry has to run in a clean one, where it now finds the winner's row and updates it.
    private <T> T writeWithRetry(Supplier<T> write) {
        try {
            return transactionTemplate.execute(status -> write.get());
        } catch (DataIntegrityViolationException e) {
            return transactionTemplate.execute(status -> write.get());
        }
    }

    private NotificationSettingResponse applyUpdate(Long userId, NotificationType type,
                                                    NotificationSettingRequest request) {
        NotificationSettings settings = notificationSettingsRepository
                .findByUserIdUserAndType(userId, type)
                .orElseGet(() -> newRow(userId, type));

        settings.setInApp(request.getInApp());
        settings.setEmail(request.getEmail());

        return NotificationSettingResponse.from(notificationSettingsRepository.save(settings));
    }

    private List<NotificationSettingResponse> applyBatch(Long userId,
                                                         NotificationSettingsBatchRequest request) {
        Map<NotificationType, NotificationSettings> existing = savedByType(userId);

        for (NotificationSettingEntry entry : request.getSettings()) {
            NotificationSettings settings = existing.get(entry.getType());
            if (settings == null) {
                settings = newRow(userId, entry.getType());
            }
            settings.setInApp(entry.getInApp());
            settings.setEmail(entry.getEmail());
            notificationSettingsRepository.save(settings);
        }

        // Read back rather than merging in memory, so the response is the persisted state.
        return effectiveMatrix(userId);
    }

    private List<NotificationSettingResponse> effectiveMatrix(Long userId) {
        Map<NotificationType, NotificationSettings> saved = savedByType(userId);

        return Arrays.stream(NotificationType.values())
                .map(type -> saved.containsKey(type)
                        ? NotificationSettingResponse.from(saved.get(type))
                        : NotificationSettingResponse.defaults(type))
                .toList();
    }

    private Map<NotificationType, NotificationSettings> savedByType(Long userId) {
        return notificationSettingsRepository.findByUserIdUser(userId).stream()
                .collect(Collectors.toMap(NotificationSettings::getType, Function.identity()));
    }

    private NotificationSettings newRow(Long userId, NotificationType type) {
        return NotificationSettings.builder().user(requireUser(userId)).type(type).build();
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));
    }
}
