package com.tasklean.api.domain.auditlog;

import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.task.Task;
import com.tasklean.api.domain.task.TaskRepository;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns the internal {@code entity_id} an audit entry stores into the identifier the API may hand
 * out. Resolved for a whole page at once - one query per entity type present, never one per row -
 * because an audit feed mixes types and the ids are the only reference the trail keeps.
 */
@Component
@RequiredArgsConstructor
public class AuditEntityRefs {

    private final TaskRepository taskRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    /**
     * Resolves the external reference for every entry in a page.
     *
     * @param entries the audit entries about to be mapped
     * @return the reference per {@code (entityType, entityId)}; an entry whose target no longer
     *         exists is absent, which the response renders as no reference rather than failing
     */
    public Map<EntityKey, String> resolve(List<AuditLog> entries) {
        Map<String, Set<Long>> idsByType = entries.stream().collect(Collectors.groupingBy(
                AuditLog::getEntityType, Collectors.mapping(AuditLog::getEntityId, Collectors.toSet())));

        Map<EntityKey, String> refs = new HashMap<>();
        idsByType.forEach((type, ids) -> uidsFor(type, ids)
                .forEach((id, uid) -> refs.put(new EntityKey(type, id), uid)));
        return refs;
    }

    private Map<Long, String> uidsFor(String entityType, Set<Long> ids) {
        return switch (entityType) {
            case AuditEntityType.TASK ->
                    index(taskRepository.findAllById(ids), Task::getIdTask, Task::getUid);
            case AuditEntityType.GROUP ->
                    index(groupRepository.findAllById(ids), Group::getIdGroup, Group::getUid);
            case AuditEntityType.GROUP_MEMBER ->
                    index(groupMemberRepository.findAllById(ids), GroupMember::getIdGroupMember, GroupMember::getUid);
            case AuditEntityType.USER ->
                    index(userRepository.findAllById(ids), User::getIdUser, User::getUid);
            // Category, Tag and TaskCompletion are addressed by their Long id everywhere else in
            // the API, so that id already is their external reference - nothing to look up.
            default -> ids.stream().collect(Collectors.toMap(Function.identity(), String::valueOf));
        };
    }

    private <T> Map<Long, String> index(Collection<T> rows, Function<T, Long> id, Function<T, String> uid) {
        return rows.stream().collect(Collectors.toMap(id, uid));
    }

    /** Identifies one audited entity: the trail is polymorphic, so the type is part of the key. */
    public record EntityKey(String entityType, Long entityId) {
    }
}
