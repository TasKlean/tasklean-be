package com.tasklean.api.domain.device;

import com.tasklean.api.common.BaseEntity;
import com.tasklean.api.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "device")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_device")
    private Long idDevice;

    // Nullable: a device created at sign-in has no push token until the client registers for push.
    @Column(name = "device_token", unique = true, length = 500)
    private String deviceToken;

    // Stable, client-generated id (X-Device-Id header) that identifies this device across logins
    // and token rotation. Unique per user. Null for push-only or legacy rows.
    @Column(name = "client_device_id")
    private String clientDeviceId;

    @Column(name = "device_type", nullable = false, length = 50)
    private String deviceType;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "browser_info")
    private String browserInfo;

    @Column(name = "os_version", length = 50)
    private String osVersion;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "app_version", length = 50)
    private String appVersion;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_used")
    private LocalDateTime lastUsed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
