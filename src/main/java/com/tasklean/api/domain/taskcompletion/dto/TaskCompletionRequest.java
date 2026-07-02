package com.tasklean.api.domain.taskcompletion.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskCompletionRequest {

    @NotNull
    private Long taskId;

    @NotNull
    private Long groupMemberId;

    private String completionPhotoUrl;

    private String completionNote;
}
