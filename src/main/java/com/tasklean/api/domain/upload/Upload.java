package com.tasklean.api.domain.upload;

import com.tasklean.api.common.BaseEntity;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "upload")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Upload extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_upload")
    private Long idUpload;

    @Column(name = "uid", unique = true, nullable = false, length = 100)
    private String uid;

    // Where the object actually lives. Internal: the API addresses an upload by its uid.
    @Column(name = "storage_key", unique = true, nullable = false, length = 200)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 50)
    private UploadPurpose purpose;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UploadStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // The group the upload is for, which is also the boundary its authorization is checked against.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @Column(name = "date_attached")
    private LocalDateTime dateAttached;
}
