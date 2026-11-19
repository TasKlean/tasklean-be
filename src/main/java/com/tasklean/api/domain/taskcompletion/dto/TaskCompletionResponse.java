package com.tasklean.api.domain.taskcompletion.dto;

import com.tasklean.api.domain.taskcompletion.TaskCompletion;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskCompletionResponse {

    private Long id;
    private String completionPhotoUid;
    private String completionNote;
    private LocalDateTime dateCompleted;
    private LocalDateTime dateCreated;
    private Long taskId;
    private Long groupMemberId;

    public static TaskCompletionResponse from(TaskCompletion completion) {
        return TaskCompletionResponse.builder()
                .id(completion.getIdTaskCompletion())
                .completionPhotoUid(completion.getCompletionPhotoUid())
                .completionNote(completion.getCompletionNote())
                .dateCompleted(completion.getDateCompleted())
                .dateCreated(completion.getDateCreated())
                .taskId(completion.getTask().getIdTask())
                .groupMemberId(completion.getAttributedTo() != null ? completion.getAttributedTo().getIdGroupMember() : null)
                .build();
    }
}
