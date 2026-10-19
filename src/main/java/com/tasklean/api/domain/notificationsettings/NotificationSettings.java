package com.tasklean.api.domain.notificationsettings;

import com.tasklean.api.common.BaseEntity;
import com.tasklean.api.domain.notification.NotificationType;
import com.tasklean.api.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "notification_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSettings extends BaseEntity {

    // What a channel is set to until the user changes it. A missing row means both of these,
    // so there is no backfill; per-type defaults can be tuned when delivery is built.
    public static final boolean DEFAULT_IN_APP = true;
    public static final boolean DEFAULT_EMAIL = true;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notification_settings")
    private Long idNotificationSettings;

    // One row per (user, type) — enforced by the unique constraint in the migration.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    @Builder.Default
    @Column(name = "in_app", nullable = false)
    private Boolean inApp = DEFAULT_IN_APP;

    @Builder.Default
    @Column(name = "email", nullable = false)
    private Boolean email = DEFAULT_EMAIL;
}
