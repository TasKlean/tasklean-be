package com.tasklean.api.domain.group;

import com.tasklean.api.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "\"group\"")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Group extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_group")
    private Long idGroup;

    @Column(name = "uid", unique = true, nullable = false, length = 500)
    private String uid;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // The uid of our own upload, not a URL: see PROJECT_BIBLE "Image uploads".
    @Column(name = "photo_uid", length = 100)
    private String photoUid;

    @Column(name = "invite_code", unique = true, nullable = false, length = 50)
    private String inviteCode;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}