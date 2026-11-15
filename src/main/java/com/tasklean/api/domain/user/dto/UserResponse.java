package com.tasklean.api.domain.user.dto;

import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRole;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A user's own profile, or one read by platform staff. Exactly one of {@code photoUid} and
 * {@code photoUrl} is ever set - see {@code ProfilePhoto}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private String uid;
    private String email;
    private String name;
    private String middleName;
    private String lastName;
    private String photoUid;
    private String photoUrl;
    private Boolean isEmailVerified;
    private Boolean isActive;
    private UserRole role;
    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;

    public static UserResponse from(User user) {
        ProfilePhoto photo = ProfilePhoto.of(user);
        return UserResponse.builder()
                .uid(user.getUid())
                .email(user.getEmail())
                .name(user.getName())
                .middleName(user.getMiddleName())
                .lastName(user.getLastName())
                .photoUid(photo.uid())
                .photoUrl(photo.url())
                .isEmailVerified(user.getIsEmailVerified())
                .isActive(user.getIsActive())
                .role(user.getRole())
                .dateCreated(user.getDateCreated())
                .dateUpdated(user.getDateUpdated())
                .build();
    }
}
