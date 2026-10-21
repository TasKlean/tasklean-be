package com.tasklean.api.domain.notificationsettings;

import com.tasklean.api.domain.notification.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for per-user notification settings — one row per (user, notification type), always
 * looked up scoped to the owning user.
 */
@Repository
public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Long> {

    List<NotificationSettings> findByUserIdUser(Long userId);

    Optional<NotificationSettings> findByUserIdUserAndType(Long userId, NotificationType type);
}
