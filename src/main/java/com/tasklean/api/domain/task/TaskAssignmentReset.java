package com.tasklean.api.domain.task;

import com.tasklean.api.domain.groupmember.GroupMember;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Hands a chore back to whoever it normally belongs to once a round is over. The assignee answers
 * who has this round, the default answers whose chore it is, and a round ending returns one to the
 * other.
 *
 * <p>Its own component because two paths end a round - somebody recording one, and the sweeper
 * writing off one nobody did - and a chore must come back the same way down both.
 */
@Component
public class TaskAssignmentReset {

    /**
     * Hands the chore back to its default assignee, which may be nobody at all.
     *
     * @param task the chore whose round has ended
     * @return {@code true} if the chore changed hands, so a caller can record that it did
     */
    public boolean toDefault(Task task) {
        Long outgoing = idOf(task.getAssignedTo());
        GroupMember owner = task.getDefaultAssignee();
        task.setAssignedTo(owner);
        return !Objects.equals(outgoing, idOf(owner));
    }

    // Compared by id: two loads of one row are not the same object, so comparing the entities
    // would call every hand-back a change.
    private Long idOf(GroupMember member) {
        return member != null ? member.getIdGroupMember() : null;
    }
}
