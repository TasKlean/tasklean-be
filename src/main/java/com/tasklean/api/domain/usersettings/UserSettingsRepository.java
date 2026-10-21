package com.tasklean.api.domain.usersettings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Data access for per-user UI settings — one row per user, looked up by the owning user's id. */
@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    Optional<UserSettings> findByUserIdUser(Long userId);
}
