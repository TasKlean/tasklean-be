package com.tasklean.api.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserRequest {

    @NotBlank
    private String email;

    @NotBlank
    private String name;

    private String middleName;

    @NotBlank
    private String lastName;

    private String photoUrl;
}
