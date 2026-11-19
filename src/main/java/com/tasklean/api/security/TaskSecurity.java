package com.tasklean.api.security;

import com.tasklean.api.domain.task.Task;
import com.tasklean.api.domain.task.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;

/**
 * Authorization for a single task, referenced as {@code @taskSecurity} from {@code @PreAuthorize}.
 * A task's household is its boundary, so every check resolves the group first and then asks
 * {@link GroupSecurity} about it.
 *
 * <p>Reads ids only — never a lazy hop to a user — because an expression is evaluated before the
 * call, outside any transaction, and {@code spring.jpa.open-in-view} is off.
 */
@Component("taskSecurity")
@RequiredArgsConstructor
public class TaskSecurity {

    private final TaskRepository taskRepository;
    private final GroupSecurity groupSecurity;

    /**
     * Whether the caller may read this task — i.e. is an active member of its household.
     *
     * @param taskUid the task's public UID
     * @return {@code true} if the caller shares the household
     */
    public boolean canView(String taskUid) {
        return onTasksGroup(taskUid, groupSecurity::isMemberOfGroup);
    }

    /**
     * Whether the caller may change this task — edit it, delete it or archive it. Its author may,
     * because it is theirs; a group admin may, because somebody has to be able to tidy up after a
     * member who has lost interest in a chore they created.
     *
     * @param taskUid the task's public UID
     * @return {@code true} if the caller created the task or administers its household
     */
    public boolean canEdit(String taskUid) {
        return taskRepository.findByUid(taskUid)
                .map(task -> {
                    Long groupId = task.getGroup().getIdGroup();
                    return groupSecurity.isAdminOfGroup(groupId) || isAuthor(task, groupId);
                })
                .orElse(false);
    }

    // Authorship is recorded as a membership, and a user has at most one of those per group, so
    // comparing membership ids answers it without loading either row's user.
    private boolean isAuthor(Task task, Long groupId) {
        return groupSecurity.callersMembershipId(groupId)
                .map(callers -> callers.equals(task.getCreatedBy().getIdGroupMember()))
                .orElse(false);
    }

    // Unfiltered: a soft-deleted task still resolves here so the service can answer 404, which is
    // what keeps a member's "gone" distinct from a stranger's "none of your business".
    private boolean onTasksGroup(String taskUid, Predicate<Long> check) {
        return taskRepository.findByUid(taskUid)
                .map(task -> check.test(task.getGroup().getIdGroup()))
                .orElse(false);
    }
}
