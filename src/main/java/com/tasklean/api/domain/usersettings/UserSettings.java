package com.tasklean.api.domain.usersettings;

import com.tasklean.api.common.BaseEntity;
import com.tasklean.api.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettings extends BaseEntity {

    // The settings a user has until they save their own; also the column DEFAULTs in the migration.
    public static final Theme DEFAULT_THEME = Theme.SYSTEM;
    public static final AppLocale DEFAULT_LOCALE = AppLocale.EN;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_user_settings")
    private Long idUserSettings;

    // One row per user: @ManyToOne with a unique join column, keeping the codebase's
    // @ManyToOne-only relationship style while the DB constraint enforces the 1:1.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "theme", nullable = false, length = 20)
    private Theme theme = DEFAULT_THEME;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "locale", nullable = false, length = 10)
    private AppLocale locale = DEFAULT_LOCALE;
}
