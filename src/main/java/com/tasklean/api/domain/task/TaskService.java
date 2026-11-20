package com.tasklean.api.domain.task;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.ErrorCodes;
import com.tasklean.api.common.exception.BusinessRuleException;
import com.tasklean.api.common.exception.InvalidRequestException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditChanges;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.category.Category;
import com.tasklean.api.domain.category.CategoryRepository;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.tag.Tag;
import com.tasklean.api.domain.tag.TagRepository;
import com.tasklean.api.domain.tag.TagResolver;
import com.tasklean.api.domain.tag.TaskTag;
import com.tasklean.api.domain.tag.TaskTagRepository;
import com.tasklean.api.domain.task.dto.AssignTaskRequest;
import com.tasklean.api.domain.task.dto.TaskBoardFilterRequest;
import com.tasklean.api.domain.task.dto.TaskBoardPage;
import com.tasklean.api.domain.task.dto.TaskCreateRequest;
import com.tasklean.api.domain.task.dto.TaskResponse;
import com.tasklean.api.domain.task.dto.TaskUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages the tasks of a household — creating one, editing it, retiring it and ending it. The
 * board that lists them, and completing one, live elsewhere.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final TaskTagRepository taskTagRepository;
    private final TagResolver tagResolver;
    private final TaskBoardFilter taskBoardFilter;
    private final RecurrenceSchedule recurrenceSchedule;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /**
     * Returns one page of a household's task board, narrowed and ordered by the request.
     *
     * @param groupUid the household's public UID
     * @param query    what the caller asked for
     * @return the matching page, with the totals the caller needs to render page controls
     * @throws ResourceNotFoundException if no active group has that UID
     * @throws InvalidRequestException   if the filters contradict each other
     */
    public TaskBoardPage getBoard(String groupUid, TaskBoardFilterRequest query) {
        Group group = groupRepository.findByUidAndIsActiveTrue(groupUid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));
        Page<Task> page = taskRepository.findAll(
                taskBoardFilter.narrow(group.getIdGroup(), query), taskBoardFilter.pageFor(query));

        LocalDateTime now = LocalDateTime.now(clock);
        Map<Long, List<Tag>> tags = tagsFor(page.getContent());
        return TaskBoardPage.builder()
                .items(page.getContent().stream()
                        .map(task -> TaskResponse.from(
                                task, tags.getOrDefault(task.getIdTask(), List.of()), now))
                        .toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalPages(page.getTotalPages())
                .totalTasks(page.getTotalElements())
                .build();
    }

    /**
     * Returns a task by its public UID.
     *
     * @param uid the task's public UID
     * @return the task
     * @throws ResourceNotFoundException if no active task has that UID
     */
    public TaskResponse getTaskByUid(String uid) {
        Task task = taskRepository.findDetailedByUidAndIsActiveTrue(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TASK_NOT_FOUND));
        return TaskResponse.from(task, tagsOf(task), LocalDateTime.now(clock));
    }

    /**
     * Creates a task in a household, authored by the caller.
     *
     * @param request      the task's details and the household it belongs to
     * @param callerUserId the id of the authenticated caller, recorded as the author
     * @return the created task
     * @throws ResourceNotFoundException if the group, the assignee or the category is not part of
     *                                   the caller's household
     * @throws InvalidRequestException   if a recurring task arrives without a due date, or declares
     *                                   a recurrence that cannot be scheduled
     */
    @Transactional
    public TaskResponse createTask(TaskCreateRequest request, Long callerUserId) {
        Group group = groupRepository.findByUidAndIsActiveTrue(request.getGroupUid())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));
        // Authorship comes from the token: the request has no say in who it is attributed to.
        GroupMember author = groupMemberRepository
                .findByUserIdUserAndGroupIdGroup(callerUserId, group.getIdGroup())
                .filter(member -> Boolean.TRUE.equals(member.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_MEMBER_NOT_FOUND));

        LocalDateTime nextDueDate = utc(request.getNextDueDate());
        requireDueDateForRecurrence(request.getRecurrenceType(), nextDueDate);
        List<Tag> tags = tagResolver.resolve(group, request.getTagNames());

        Task task = Task.builder()
                .uid(UUID.randomUUID().toString())
                .name(request.getName())
                .description(request.getDescription())
                .priority(request.getPriority())
                .recurrenceType(request.getRecurrenceType())
                // Derived, never sent: a pattern a caller chose could contradict the due date.
                .recurrencePattern(anchorFor(request.getRecurrenceType(), nextDueDate))
                .nextDueDate(nextDueDate)
                .requiresPhotoProof(Boolean.TRUE.equals(request.getRequiresPhotoProof()))
                .estimatedTimeMinutes(request.getEstimatedTimeMinutes())
                .status(TaskStatus.TODO)
                .isActive(true)
                .group(group)
                .createdBy(author)
                .assignedTo(assigneeIn(group, request.getAssignedToUid()))
                .category(categoryIn(group, request.getCategoryId()))
                .build();

        Task saved = taskRepository.save(task);
        syncTags(saved, List.of(), tags);
        log.info("Task created: uid={} groupUid={}", saved.getUid(), group.getUid());
        auditLogService.recordEvent(AuditEntityType.TASK, saved.getIdTask(), AuditAction.CREATE,
                auditMessage(saved.getName(), "created"), group);
        return TaskResponse.from(saved, tags, LocalDateTime.now(clock));
    }

    /**
     * Updates a task's details. Its household and its author are not editable, and neither is its
     * status.
     *
     * @param uid     the task's public UID
     * @param request the new details
     * @return the updated task
     * @throws ResourceNotFoundException if no active task has that UID, or the assignee or category
     *                                   is not part of its household
     * @throws InvalidRequestException   if a recurring task arrives without a due date, or declares
     *                                   a recurrence that cannot be scheduled
     */
    @Transactional
    public TaskResponse updateTask(String uid, TaskUpdateRequest request) {
        Task task = taskRepository.findByUidAndIsActiveTrue(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TASK_NOT_FOUND));
        Group group = task.getGroup();

        LocalDateTime nextDueDate = utc(request.getNextDueDate());
        requireDueDateForRecurrence(request.getRecurrenceType(), nextDueDate);
        // Both resolved before the tracked sets below, so a reference into another household is
        // refused before any of this task's own fields have moved.
        GroupMember assignee = assigneeIn(group, request.getAssignedToUid());
        Category category = categoryIn(group, request.getCategoryId());
        // Resolved before the task's own tags are read, because resolving may revive a deleted
        // tag that is already on this task - read first, its join row would look missing and be
        // inserted a second time.
        List<Tag> tags = tagResolver.resolve(group, request.getTagNames());
        List<Tag> current = tagsOf(task);

        AuditChanges changes = AuditChanges.create()
                .set("name", task::getName, task::setName, request.getName())
                .set("description", task::getDescription, task::setDescription, request.getDescription())
                // Values kept from here down: each is a fixed token, a flag, a number or a date, so
                // keeping it discloses nothing a reader cannot already see on the task itself.
                .setVisible("priority", task::getPriority, task::setPriority, request.getPriority())
                .setVisible("recurrenceType", task::getRecurrenceType, task::setRecurrenceType,
                        request.getRecurrenceType())
                .setVisible("nextDueDate", task::getNextDueDate, task::setNextDueDate, nextDueDate)
                .setVisible("requiresPhotoProof", task::getRequiresPhotoProof, task::setRequiresPhotoProof,
                        Boolean.TRUE.equals(request.getRequiresPhotoProof()))
                .setVisible("estimatedTimeMinutes", task::getEstimatedTimeMinutes,
                        task::setEstimatedTimeMinutes, request.getEstimatedTimeMinutes())
                // Compared by id: two loads of one row are not the same object, so comparing the
                // entities would call every reassignment a change.
                .track("assignedToUid", idOf(task.getAssignedTo()), idOf(assignee))
                .track("categoryId", idOf(task.getCategory()), request.getCategoryId())
                // Names only: a tag is text a member wrote, and a group's trail is kept for
                // good and read by every one of them.
                .track("tagNames", tagIds(current), tagIds(tags));

        task.setAssignedTo(assignee);
        task.setCategory(category);
        // Re-derived rather than tracked: the anchor is a restatement of the due date, so an entry
        // naming it would say the same thing twice.
        task.setRecurrencePattern(anchorFor(task.getRecurrenceType(), task.getNextDueDate()));

        Task saved = taskRepository.save(task);
        syncTags(saved, current, tags);
        auditLogService.recordEvent(AuditEntityType.TASK, saved.getIdTask(), AuditAction.UPDATE,
                auditMessage(saved.getName(), "updated"), group, changes);
        return TaskResponse.from(saved, tags, LocalDateTime.now(clock));
    }

    /**
     * Takes an unassigned chore. Its own endpoint rather than a plain assignment, because taking
     * one needs no right over the task beyond membership - whereas handing a chore to somebody
     * else does.
     *
     * @param uid          the task's public UID
     * @param callerUserId the acting user, from the JWT
     * @return the task, now the caller's
     * @throws ResourceNotFoundException if no active task has that UID
     * @throws AccessDeniedException     if the caller is not an active member of the household
     * @throws BusinessRuleException     if somebody already has the chore
     */
    @Transactional
    public TaskResponse claimTask(String uid, Long callerUserId) {
        Task task = taskRepository.findForUpdateByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TASK_NOT_FOUND));
        GroupMember caller = callersMembership(task, callerUserId);
        if (task.getAssignedTo() != null) {
            // Taken, whether by the caller or by anybody else. Handing a chore on is a different
            // act with a different right behind it, so claiming must not quietly do it.
            throw new BusinessRuleException("Somebody already has this chore",
                    ErrorCodes.TASK_ALREADY_ASSIGNED);
        }

        AuditChanges changes = AuditChanges.create()
                .track("assignedToUid", null, caller.getIdGroupMember());
        task.setAssignedTo(caller);
        Task saved = taskRepository.save(task);

        log.info("Task claimed: uid={}", uid);
        auditLogService.recordEvent(AuditEntityType.TASK, saved.getIdTask(), AuditAction.TASK_CLAIMED,
                auditMessage(saved.getName(), "claimed"), saved.getGroup(), changes);
        return TaskResponse.from(saved, tagsOf(saved), LocalDateTime.now(clock));
    }

    /**
     * Hands a chore to a member of its household, or releases it back to the pool when no member is
     * named. Gated on the right to change the task rather than on membership: deciding who does a
     * chore is not the same as doing it.
     *
     * @param uid     the task's public UID
     * @param request who to assign it to, or nothing to release it
     * @return the task, with its new assignee
     * @throws ResourceNotFoundException if no active task has that UID, or the member named is not
     *                                   an active member of its household
     */
    @Transactional
    public TaskResponse assignTask(String uid, AssignTaskRequest request) {
        Task task = taskRepository.findByUidAndIsActiveTrue(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TASK_NOT_FOUND));
        GroupMember assignee = assigneeIn(task.getGroup(), request.getMemberUid());

        AuditChanges changes = AuditChanges.create()
                .track("assignedToUid", idOf(task.getAssignedTo()), idOf(assignee));
        task.setAssignedTo(assignee);
        Task saved = taskRepository.save(task);

        log.info("Task assignee set: uid={} assigned={}", uid, assignee != null);
        auditLogService.recordEvent(AuditEntityType.TASK, saved.getIdTask(), AuditAction.TASK_ASSIGNED,
                auditMessage(saved.getName(), assignee != null ? "assigned" : "released"),
                saved.getGroup(), changes);
        return TaskResponse.from(saved, tagsOf(saved), LocalDateTime.now(clock));
    }

    /**
     * Retires a task the household has stopped doing. It leaves every board but keeps its history,
     * which is what separates this from deleting it.
     *
     * @param uid the task's public UID
     * @return the archived task
     * @throws ResourceNotFoundException if no active task has that UID
     */
    @Transactional
    public TaskResponse archiveTask(String uid) {
        Task task = taskRepository.findByUidAndIsActiveTrue(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TASK_NOT_FOUND));
        AuditChanges changes = AuditChanges.create()
                .setVisible("status", task::getStatus, task::setStatus, TaskStatus.ARCHIVED);
        Task saved = taskRepository.save(task);
        log.info("Task archived: uid={}", uid);
        auditLogService.recordEvent(AuditEntityType.TASK, saved.getIdTask(), AuditAction.UPDATE,
                auditMessage(saved.getName(), "archived"), saved.getGroup(), changes);
        return TaskResponse.from(saved, tagsOf(saved), LocalDateTime.now(clock));
    }

    /**
     * Soft-deletes a task.
     *
     * @param uid the task's public UID
     * @throws ResourceNotFoundException if no active task has that UID
     */
    @Transactional
    public void deleteTask(String uid) {
        // Filtered, so deleting twice is a 404 rather than a second entry on the task's trail.
        Task task = taskRepository.findByUidAndIsActiveTrue(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TASK_NOT_FOUND));
        task.setIsActive(false);
        taskRepository.save(task);
        log.info("Task soft-deleted: uid={}", uid);
        auditLogService.recordEvent(AuditEntityType.TASK, task.getIdTask(), AuditAction.DELETE,
                auditMessage(task.getName(), "deleted"), task.getGroup());
    }

    // A recurring task without a due date has no schedule to recur on, and nothing later would
    // give it one.
    private void requireDueDateForRecurrence(RecurrenceType type, LocalDateTime nextDueDate) {
        if (type != null && nextDueDate == null) {
            throw new InvalidRequestException("A recurring task needs a due date to recur from");
        }
    }

    /**
     * Brings a task's join rows in line with the tags it should carry, writing only the
     * difference. Reinserting every row on each save would reset {@code date_created} on rows
     * nothing touched, which is the only record of when a task was tagged.
     *
     * <p>Rows pointing at a <em>deleted</em> tag are not in {@code current} and so are left
     * alone. The caller cannot see those labels, so an edit of theirs is no reason to drop
     * them - and dropping them would stop reviving the tag from bringing it back here.
     */
    private void syncTags(Task task, List<Tag> current, List<Tag> wanted) {
        Set<Long> currentIds = ids(current);
        Set<Long> wantedIds = ids(wanted);
        // Hard-deleted, unlike everything else here: a join row holds no state of its own, so
        // there is nothing left to keep once the label comes off the task.
        current.stream()
                .filter(tag -> !wantedIds.contains(tag.getIdTag()))
                .forEach(tag -> taskTagRepository
                        .deleteByTaskIdTaskAndTagIdTag(task.getIdTask(), tag.getIdTag()));
        wanted.stream()
                .filter(tag -> !currentIds.contains(tag.getIdTag()))
                .forEach(tag -> taskTagRepository
                        .save(TaskTag.builder().task(task).tag(tag).build()));
    }

    private List<Tag> tagsOf(Task task) {
        return tagRepository.findActiveByTask(task.getIdTask());
    }

    /**
     * The live tags of a whole board page, by task id. One query for the page rather than one
     * per task, which is why a task entity carries no tag collection of its own.
     */
    private Map<Long, List<Tag>> tagsFor(List<Task> tasks) {
        if (tasks.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = tasks.stream().map(Task::getIdTask).toList();
        return taskTagRepository.findActiveByTasks(ids).stream().collect(Collectors.groupingBy(
                link -> link.getTask().getIdTask(),
                Collectors.mapping(TaskTag::getTag, Collectors.toList())));
    }

    private static Set<Long> ids(List<Tag> tags) {
        return tags.stream().map(Tag::getIdTag).collect(Collectors.toSet());
    }

    // Sorted, so the comparison is about which tags the task carries rather than what order
    // they arrived in. The ids never leave this method: track() keeps neither value.
    private static List<Long> tagIds(List<Tag> tags) {
        return tags.stream().map(Tag::getIdTag).sorted().toList();
    }

    private Map<String, Object> anchorFor(RecurrenceType type, LocalDateTime nextDueDate) {
        return type == null ? null : recurrenceSchedule.anchorFor(type, nextDueDate);
    }

    /**
     * The membership named by the caller, or null when nobody was named — an unassigned task sits
     * in the household's pool for anybody to claim.
     *
     * <p>A membership of another household answers "not found" rather than "not allowed": a 403
     * would confirm the uid belongs to somebody, somewhere.
     */
    // The caller as a member of this task's household. Resolved here rather than trusted from
    // the expression layer, which answers only whether they may call at all.
    private GroupMember callersMembership(Task task, Long callerUserId) {
        if (callerUserId == null) {
            throw new AccessDeniedException(ErrorMessages.ACCESS_DENIED);
        }
        return groupMemberRepository
                .findByUserIdUserAndGroupIdGroup(callerUserId, task.getGroup().getIdGroup())
                .filter(member -> Boolean.TRUE.equals(member.getIsActive()))
                .orElseThrow(() -> new AccessDeniedException(ErrorMessages.ACCESS_DENIED));
    }

    private GroupMember assigneeIn(Group group, String memberUid) {
        if (memberUid == null) {
            return null;
        }
        return groupMemberRepository.findByUid(memberUid)
                .filter(member -> Boolean.TRUE.equals(member.getIsActive()))
                .filter(member -> member.getGroup().getIdGroup().equals(group.getIdGroup()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.ASSIGNED_MEMBER_NOT_FOUND));
    }

    // Categories are scoped to a household too, so one from another household is as absent as one
    // that never existed.
    private Category categoryIn(Group group, Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .filter(category -> Boolean.TRUE.equals(category.getIsActive()))
                .filter(category -> category.getGroup().getIdGroup().equals(group.getIdGroup()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.CATEGORY_NOT_FOUND));
    }

    // The column holds UTC with no zone of its own, so the caller's offset is applied here rather
    // than compared against it - the request states an instant, the column stores one.
    private LocalDateTime utc(OffsetDateTime value) {
        return value != null ? value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime() : null;
    }

    private static String auditMessage(String name, String verb) {
        return "Task \"" + name + "\" " + verb;
    }

    // An id off a lazy association is served by the proxy, so neither of these loads the row.
    private static Long idOf(GroupMember member) {
        return member != null ? member.getIdGroupMember() : null;
    }

    private static Long idOf(Category category) {
        return category != null ? category.getIdCategory() : null;
    }
}
