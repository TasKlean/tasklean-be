package com.tasklean.api.domain.user.dto;

import com.tasklean.api.domain.user.User;
import lombok.*;

/**
 * The slice of a user that other people are allowed to see — enough to render them in a list.
 * Deliberately omits email and account state; those belong to the account's owner and to staff.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSummaryResponse {

    private String uid;
    private String name;
    private String lastName;
    private String photoUrl;

    public static UserSummaryResponse from(User user) {
        return UserSummaryResponse.builder()
                .uid(user.getUid())
                .name(user.getName())
                .lastName(user.getLastName())
                .photoUrl(user.getPhotoUrl())
                .build();
    }
}
