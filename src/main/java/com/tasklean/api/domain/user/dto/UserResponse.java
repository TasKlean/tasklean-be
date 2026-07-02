package com.tasklean.api.domain.user.dto;

import com.tasklean.api.domain.user.User;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private Long id;
    private String uid;
    private String email;
    private String name;
    private String middleName;
    private String lastName;
    private String photoUrl;
    private Boolean isActive;
    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getIdUser())
                .uid(user.getUid())
                .email(user.getEmail())
                .name(user.getName())
                .middleName(user.getMiddleName())
                .lastName(user.getLastName())
                .photoUrl(user.getPhotoUrl())
                .isActive(user.getIsActive())
                .dateCreated(user.getDateCreated())
                .dateUpdated(user.getDateUpdated())
                .build();
    }
}
