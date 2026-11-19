package com.tasklean.api.domain.task;

import com.tasklean.api.common.exception.InvalidRequestException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditChanges;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditFieldChange;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.category.Category;
import com.tasklean.api.domain.category.CategoryRepository;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.task.dto.TaskCreateRequest;
import com.tasklean.api.domain.task.dto.TaskUpdateRequest;
import com.tasklean.api.domain.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private CategoryRepository categoryRepository;
    // The real one: the anchor it derives is part of what these tests are checking.
    @Spy
    private RecurrenceSchedule recurrenceSchedule = new RecurrenceSchedule();
    @Mock
    private AuditLogService auditLogService;
    @InjectMocks
    private TaskService taskService;

    private static final Long GROUP_ID = 10L;
    private static final Long CALLER_USER_ID = 99L;
    private static final Long AUTHOR_ID = 5L;

    private Group group() {
        return Group.builder().idGroup(GROUP_ID).uid("grp-1").isActive(true).build();
    }

    private GroupMember member(Long id) {
        return member(id, group());
    }

    private GroupMember member(Long id, Group group) {
        return GroupMember.builder().idGroupMember(id).uid("gm-" + id).isActive(true).group(group)
                .user(User.builder().idUser(id).uid("usr-" + id).name("Member").build()).build();
    }

    private Task task() {
        return Task.builder().idTask(1L).uid("tsk-1").name("Clean").priority(TaskPriority.LOW)
                .status(TaskStatus.TODO).requiresPhotoProof(false).isActive(true)
                .group(group()).createdBy(member(AUTHOR_ID)).build();
    }

    private TaskCreateRequest createRequest() {
        TaskCreateRequest r = new TaskCreateRequest();
        r.setGroupUid("grp-1");
        r.setName("Clean");
        r.setPriority(TaskPriority.MEDIUM);
        return r;
    }

    private TaskUpdateRequest updateRequest() {
        TaskUpdateRequest r = new TaskUpdateRequest();
        r.setName("Clean");
        r.setPriority(TaskPriority.LOW);
        return r;
    }

    private void callerIsAMember() {
        when(groupRepository.findByUidAndIsActiveTrue("grp-1")).thenReturn(Optional.of(group()));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(CALLER_USER_ID, GROUP_ID))
                .thenReturn(Optional.of(member(AUTHOR_ID)));
    }

    private void savesWhatItIsGiven() {
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Task savedTask() {
        ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(saved.capture());
        return saved.getValue();
    }

    private AuditChanges capturedChanges() {
        ArgumentCaptor<AuditChanges> changes = ArgumentCaptor.forClass(AuditChanges.class);
        verify(auditLogService).recordEvent(eq(AuditEntityType.TASK), eq(1L), eq(AuditAction.UPDATE),
                any(), any(), changes.capture());
        return changes.getValue();
    }

    @Test
    void getTaskByUid_found_returnsIt() {
        when(taskRepository.findDetailedByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.of(task()));

        assertThat(taskService.getTaskByUid("tsk-1").getUid()).isEqualTo("tsk-1");
    }

    @Test
    void getTaskByUid_deleted_isNotFound() {
        // The filtered finder is the whole guard: a soft-deleted task stays resolvable for the
        // authorization layer, so only this lookup keeps it out of a response.
        when(taskRepository.findDetailedByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTaskByUid("tsk-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createTask_attributesItToTheCallerNotTheRequest() {
        callerIsAMember();
        savesWhatItIsGiven();

        taskService.createTask(createRequest(), CALLER_USER_ID);

        assertThat(savedTask().getCreatedBy().getIdGroupMember()).isEqualTo(AUTHOR_ID);
    }

    @Test
    void createTask_startsItWaitingToBeDone() {
        callerIsAMember();
        savesWhatItIsGiven();

        taskService.createTask(createRequest(), CALLER_USER_ID);

        assertThat(savedTask().getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void createTask_callerIsNotAMemberOfTheHousehold_isNotFound() {
        when(groupRepository.findByUidAndIsActiveTrue("grp-1")).thenReturn(Optional.of(group()));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(CALLER_USER_ID, GROUP_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.createTask(createRequest(), CALLER_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createTask_deletedHousehold_isNotFound() {
        when(groupRepository.findByUidAndIsActiveTrue("grp-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.createTask(createRequest(), CALLER_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createTask_monthly_anchorsItToTheFirstDueDay() {
        callerIsAMember();
        savesWhatItIsGiven();
        TaskCreateRequest r = createRequest();
        r.setRecurrenceType(RecurrenceType.MONTHLY);
        r.setNextDueDate(OffsetDateTime.parse("2026-11-29T09:00Z"));

        taskService.createTask(r, CALLER_USER_ID);

        assertThat(savedTask().getRecurrencePattern()).containsExactly(Map.entry("dayOfMonth", 29));
    }

    @Test
    void createTask_daily_storesNoPattern() {
        callerIsAMember();
        savesWhatItIsGiven();
        TaskCreateRequest r = createRequest();
        r.setRecurrenceType(RecurrenceType.DAILY);
        r.setNextDueDate(OffsetDateTime.parse("2026-11-20T09:00Z"));

        taskService.createTask(r, CALLER_USER_ID);

        // Nothing to anchor: a daily task recurs on the day after whatever it is due.
        assertThat(savedTask().getRecurrencePattern()).isNull();
    }

    @Test
    void createTask_recurringWithoutADueDate_isRefused() {
        callerIsAMember();
        TaskCreateRequest r = createRequest();
        r.setRecurrenceType(RecurrenceType.WEEKLY);

        assertThatThrownBy(() -> taskService.createTask(r, CALLER_USER_ID))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createTask_oneOffWithoutADueDate_isAllowed() {
        callerIsAMember();
        savesWhatItIsGiven();

        taskService.createTask(createRequest(), CALLER_USER_ID);

        assertThat(savedTask().getNextDueDate()).isNull();
    }

    @Test
    void createTask_dueDate_isStoredAsUtc() {
        callerIsAMember();
        savesWhatItIsGiven();
        TaskCreateRequest r = createRequest();
        r.setNextDueDate(OffsetDateTime.parse("2026-11-23T09:00+02:00"));

        taskService.createTask(r, CALLER_USER_ID);

        assertThat(savedTask().getNextDueDate()).isEqualTo(LocalDateTime.parse("2026-11-23T07:00"));
    }

    @Test
    void createTask_assigneeFromAnotherHousehold_isNotFound() {
        callerIsAMember();
        // A real membership, from another household. Not found rather than forbidden: a 403 would
        // confirm the uid belongs to someone, somewhere.
        when(groupMemberRepository.findByUid("gm-6"))
                .thenReturn(Optional.of(member(6L, Group.builder().idGroup(77L).build())));
        TaskCreateRequest r = createRequest();
        r.setAssignedToUid("gm-6");

        assertThatThrownBy(() -> taskService.createTask(r, CALLER_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createTask_assigneeWhoHasLeft_isNotFound() {
        callerIsAMember();
        GroupMember former = member(6L);
        former.setIsActive(false);
        when(groupMemberRepository.findByUid("gm-6")).thenReturn(Optional.of(former));
        TaskCreateRequest r = createRequest();
        r.setAssignedToUid("gm-6");

        assertThatThrownBy(() -> taskService.createTask(r, CALLER_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createTask_categoryFromAnotherHousehold_isNotFound() {
        callerIsAMember();
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(Category.builder()
                .idCategory(3L).isActive(true).group(Group.builder().idGroup(77L).build()).build()));
        TaskCreateRequest r = createRequest();
        r.setCategoryId(3L);

        assertThatThrownBy(() -> taskService.createTask(r, CALLER_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createTask_noAssignee_leavesItInThePool() {
        callerIsAMember();
        savesWhatItIsGiven();

        taskService.createTask(createRequest(), CALLER_USER_ID);

        assertThat(savedTask().getAssignedTo()).isNull();
    }

    @Test
    void updateTask_everyTrackedField_namesThemAll() {
        TaskUpdateRequest r = updateRequest();
        r.setName("Scrub");
        r.setDescription("the floor");
        r.setPriority(TaskPriority.HIGH);
        r.setRecurrenceType(RecurrenceType.WEEKLY);
        r.setNextDueDate(OffsetDateTime.parse("2026-11-23T09:00Z"));
        r.setRequiresPhotoProof(true);
        r.setEstimatedTimeMinutes(20);
        r.setAssignedToUid("gm-6");
        r.setCategoryId(3L);
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.of(task()));
        when(groupMemberRepository.findByUid("gm-6")).thenReturn(Optional.of(member(6L)));
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(
                Category.builder().idCategory(3L).isActive(true).group(group()).build()));
        savesWhatItIsGiven();

        taskService.updateTask("tsk-1", r);

        // Every field this endpoint can change, in the order it applies them. The household, the
        // author and the status are absent because an edit cannot touch any of them, and the
        // recurrence pattern because it is re-derived rather than tracked.
        assertThat(capturedChanges().fields()).containsExactly(
                "name", "description", "priority", "recurrenceType", "nextDueDate",
                "requiresPhotoProof", "estimatedTimeMinutes", "assignedToUid", "categoryId");
    }

    @Test
    void updateTask_everyTrackedField_disclosesOnlyTheSafeValues() {
        TaskUpdateRequest r = updateRequest();
        r.setDescription("the floor");
        r.setPriority(TaskPriority.HIGH);
        r.setEstimatedTimeMinutes(20);
        r.setAssignedToUid("gm-6");
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.of(task()));
        when(groupMemberRepository.findByUid("gm-6")).thenReturn(Optional.of(member(6L)));
        savesWhatItIsGiven();

        taskService.updateTask("tsk-1", r);

        // The tokens and the number carry their values; the text a person typed and the
        // membership's internal id do not.
        assertThat(capturedChanges().entries()).containsExactly(
                AuditFieldChange.withheld("description"),
                new AuditFieldChange("priority", "LOW", "HIGH"),
                new AuditFieldChange("estimatedTimeMinutes", null, "20"),
                AuditFieldChange.withheld("assignedToUid"));
    }

    @Test
    void updateTask_assigneeFromAnotherHousehold_changesNothingAtAll() {
        TaskUpdateRequest r = updateRequest();
        r.setName("Scrub");
        r.setAssignedToUid("gm-6");
        Task existing = task();
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.of(existing));
        when(groupMemberRepository.findByUid("gm-6"))
                .thenReturn(Optional.of(member(6L, Group.builder().idGroup(77L).build())));

        assertThatThrownBy(() -> taskService.updateTask("tsk-1", r))
                .isInstanceOf(ResourceNotFoundException.class);

        // Resolved before the tracked sets for exactly this reason: a refused reference must not
        // leave the task half-edited.
        assertThat(existing.getName()).isEqualTo("Clean");
        verify(taskRepository, never()).save(any());
    }

    @Test
    void updateTask_recurrenceChanged_reDerivesTheAnchor() {
        TaskUpdateRequest r = updateRequest();
        r.setRecurrenceType(RecurrenceType.MONTHLY);
        r.setNextDueDate(OffsetDateTime.parse("2026-12-31T09:00Z"));
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.of(task()));
        savesWhatItIsGiven();

        taskService.updateTask("tsk-1", r);

        assertThat(savedTask().getRecurrencePattern()).containsExactly(Map.entry("dayOfMonth", 31));
    }

    @Test
    void updateTask_assigneeOmitted_handsTheTaskBackToThePool() {
        Task existing = task();
        existing.setAssignedTo(member(6L));
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.of(existing));
        savesWhatItIsGiven();

        taskService.updateTask("tsk-1", updateRequest());

        assertThat(savedTask().getAssignedTo()).isNull();
    }

    @Test
    void updateTask_deleted_isNotFound() {
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.updateTask("tsk-1", updateRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void archiveTask_retiresItAndKeepsTheRow() {
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.of(task()));
        savesWhatItIsGiven();

        taskService.archiveTask("tsk-1");

        // Archiving is not deleting: the task leaves the board but its history still counts.
        assertThat(savedTask().getStatus()).isEqualTo(TaskStatus.ARCHIVED);
        assertThat(savedTask().getIsActive()).isTrue();
        assertThat(capturedChanges().entries())
                .containsExactly(new AuditFieldChange("status", "TODO", "ARCHIVED"));
    }

    @Test
    void archiveTask_alreadyArchived_writesNoEntry() {
        Task archived = task();
        archived.setStatus(TaskStatus.ARCHIVED);
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.of(archived));
        savesWhatItIsGiven();

        taskService.archiveTask("tsk-1");

        // An empty AuditChanges writes nothing, so archiving twice leaves one entry, not two.
        assertThat(capturedChanges().entries()).isEmpty();
    }

    @Test
    void archiveTask_deleted_isNotFound() {
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.archiveTask("tsk-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteTask_marksItInactive() {
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.of(task()));

        taskService.deleteTask("tsk-1");

        assertThat(savedTask().getIsActive()).isFalse();
        verify(auditLogService).recordEvent(eq(AuditEntityType.TASK), eq(1L), eq(AuditAction.DELETE),
                any(), any());
    }

    @Test
    void deleteTask_alreadyDeleted_isNotFound() {
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.deleteTask("tsk-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
