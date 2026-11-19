package com.tasklean.api.domain.task;

import com.tasklean.api.domain.groupmember.GroupMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Hands back the chores a departing member was on the hook for. A membership that ends is not done
 * ending until nothing still points at it as somebody who will act: left assigned, a task reads as
 * being somebody's when that somebody has gone, and nobody else picks it up.
 *
 * <p>Its own component rather than a method on {@link TaskService} so the two paths that end a
 * membership can call it without taking on everything else a task service does.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskAssignmentRelease {

    private final TaskRepository taskRepository;

    /**
     * Unassigns every task the membership is still expected to do, leaving them in the household's
     * pool for anybody to claim. Finished and archived tasks keep their assignee, because there it
     * records who did the work rather than who owes it.
     *
     * @param membership the membership that has ended
     * @return how many tasks were handed back
     */
    @Transactional
    public int releaseFor(GroupMember membership) {
        List<Task> assigned =
                taskRepository.findOpenAssignedTo(membership.getIdGroupMember(), TaskStatus.OPEN);
        for (Task task : assigned) {
            task.setAssignedTo(null);
            taskRepository.save(task);
        }
        if (!assigned.isEmpty()) {
            // No audit entry of its own: nobody chose this, and the entry for the membership
            // ending already says why every one of these came loose.
            log.info("Tasks unassigned with the membership: memberUid={} tasks={}",
                    membership.getUid(), assigned.size());
        }
        return assigned.size();
    }
}
