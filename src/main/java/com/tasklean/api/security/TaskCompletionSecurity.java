package com.tasklean.api.security;

import com.tasklean.api.domain.taskcompletion.TaskCompletion;
import com.tasklean.api.domain.taskcompletion.TaskCompletionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Authorization for one entry in a chore's history, referenced as {@code @taskCompletionSecurity}
 * from {@code @PreAuthorize}. An entry belongs to a task, which belongs to a household, so every
 * check resolves that household and then asks {@link GroupSecurity} about it.
 *
 * <p>Its finder fetches the task, and nothing here reads further than an id off a proxy: an
 * expression is evaluated before the call, outside any transaction, and
 * {@code spring.jpa.open-in-view} is off. A join row is one level further from the household than a
 * task is, so leaving its task a proxy made {@code getGroup()} a hop that needed the row.
 */
@Component("taskCompletionSecurity")
@RequiredArgsConstructor
public class TaskCompletionSecurity {

    private final TaskCompletionRepository taskCompletionRepository;
    private final GroupSecurity groupSecurity;

    /**
     * Whether the caller may read this entry - i.e. is an active member of the household whose
     * chore it belongs to. A household's history is the household's business, as its trail is.
     *
     * @param id the entry's id
     * @return {@code true} if the caller shares the household
     */
    public boolean canView(Long id) {
        return taskCompletionRepository.findWithTaskByIdTaskCompletion(id)
                .map(entry -> groupSecurity.isMemberOfGroup(groupIdOf(entry)))
                .orElse(false);
    }

    /**
     * Whether the caller may take this entry back. Whoever it is attributed to may, because it says
     * they did something they did not; a group admin may, because somebody has to be able to correct
     * a record for a member who is no longer around to correct it themselves.
     *
     * @param id the entry's id
     * @return {@code true} if the entry is the caller's own, or they administer the household
     */
    public boolean canUndo(Long id) {
        return taskCompletionRepository.findWithTaskByIdTaskCompletion(id)
                .map(entry -> {
                    Long groupId = groupIdOf(entry);
                    return groupSecurity.isAdminOfGroup(groupId) || isAttributedToCaller(entry, groupId);
                })
                .orElse(false);
    }

    // Attribution is a membership, and a user has at most one per group, so comparing membership
    // ids answers it without loading either row's user.
    private boolean isAttributedToCaller(TaskCompletion entry, Long groupId) {
        if (entry.getAttributedTo() == null) {
            return false;
        }
        return groupSecurity.callersMembershipId(groupId)
                .map(callers -> callers.equals(entry.getAttributedTo().getIdGroupMember()))
                .orElse(false);
    }

    // Unfiltered: an undone entry still resolves here so the service can answer 404, which is what
    // keeps a member's "gone" distinct from a stranger's "none of your business".
    private Long groupIdOf(TaskCompletion entry) {
        return entry.getTask().getGroup().getIdGroup();
    }
}
