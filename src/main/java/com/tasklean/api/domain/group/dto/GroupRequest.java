package com.tasklean.api.domain.group.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GroupRequest {

    @NotBlank
    private String name;

    private String description;

    private String photoUrl;
}
