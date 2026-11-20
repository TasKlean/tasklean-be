package com.tasklean.api.domain.task;

import com.tasklean.api.common.exception.InvalidRequestException;
import com.tasklean.api.common.exception.BusinessRuleException;
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
import com.tasklean.api.domain.tag.Tag;
import com.tasklean.api.domain.tag.TagRepository;
import com.tasklean.api.domain.tag.TagResolver;
import com.tasklean.api.domain.tag.TaskTag;
import com.tasklean.api.domain.tag.TaskTagRepository;
import com.tasklean.api.domain.task.dto.AssignTaskRequest;
import com.tasklean.api.domain.task.dto.TaskBoardFilterRequest;
import com.tasklean.api.domain.task.dto.TaskBoardPage;
import com.tasklean.api.domain.task.dto.TaskCreateRequest;
import com.tasklean.api.domain.task.dto.TaskUpdateRequest;
import com.tasklean.api.domain.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
    @Mock
    private TagRepository tagRepository;
    @Mock
    private TaskTagRepository taskTagRepository;
    @Mock
    private TagResolver tagResolver;
    @Mock
    private TaskBoardFilter taskBoardFilter;
    // The real one: the anchor it derives is part of what these tests are checking.
    @Spy
    private RecurrenceSchedule recurrenceSchedule = new RecurrenceSchedule();
    @Mock
    private AuditLogService auditLogService;
    @Spy
    private Clock clock = Clock.systemUTC();
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

    private Tag tag(Long id, String name) {
        return Tag.builder().idTag(id).name(name).isActive(true).group(group()).build();
    }

    private void resolvesTo(Tag... tags) {
        when(tagResolver.resolve(any(), any())).thenReturn(List.of(tags));
    }

    private void taskAlreadyCarries(Tag... tags) {
        when(tagRepository.findActiveByTask(1L)).thenReturn(List.of(tags));
    }

    private TaskTag savedLink() {
        ArgumentCaptor<TaskTag> saved = ArgumentCaptor.forClass(TaskTag.class);
        verify(taskTagRepository).save(saved.capture());
        return saved.getValue();
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

    private TaskBoardPage board(Task... tasks) {
        when(taskRepository.findAll(ArgumentMatchers.<Specification<Task>>any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(tasks), PageRequest.of(0, 20), tasks.length));
        when(taskBoardFilter.pageFor(any())).thenReturn(PageRequest.of(0, 20));
        return taskService.getBoard("grp-1", new TaskBoardFilterRequest());
    }

    private void householdExists() {
        when(groupRepository.findByUidAndIsActiveTrue("grp-1")).thenReturn(Optional.of(group()));
    }

    @Test
    void getBoard_matchingTasks_carriesThePageTotals() {
        householdExists();

        TaskBoardPage page = board(task());

        // The totals are the whole point of page numbers: without them a client cannot render
        // a pager at all.
        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getPage()).isZero();
        assertThat(page.getSize()).isEqualTo(20);
        assertThat(page.getTotalPages()).isEqualTo(1);
        assertThat(page.getTotalTasks()).isEqualTo(1);
    }

    @Test
    void getBoard_aPageOfTasks_loadsEveryTagInOneQuery() {
        householdExists();
        Task second = task();
        second.setIdTask(2L);
        when(taskTagRepository.findActiveByTasks(List.of(1L, 2L))).thenReturn(List.of());

        board(task(), second);

        // One query for the page rather than one per task, which is why a task carries no tag
        // collection of its own.
        verify(taskTagRepository).findActiveByTasks(List.of(1L, 2L));
        verify(tagRepository, never()).findActiveByTask(any());
    }

    @Test
    void getBoard_emptyPage_asksForNoTagsAtAll() {
        householdExists();

        assertThat(board().getItems()).isEmpty();
        verifyNoInteractions(taskTagRepository);
    }

    @Test
    void getBoard_pastDueAndStillOpen_readsAsOverdue() {
        householdExists();
        Task late = task();
        late.setNextDueDate(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));

        assertThat(board(late).getItems().getFirst().getOverdue()).isTrue();
    }

    @Test
    void getBoard_pastDueButAlreadyDone_doesNotReadAsOverdue() {
        householdExists();
        Task done = task();
        done.setNextDueDate(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        done.setStatus(TaskStatus.COMPLETED);

        // A chore completed late is history, not an outstanding job.
        assertThat(board(done).getItems().getFirst().getOverdue()).isFalse();
    }

    @Test
    void getBoard_undatedTask_doesNotReadAsOverdue() {
        householdExists();

        assertThat(board(task()).getItems().getFirst().getOverdue()).isFalse();
    }

    @Test
    void getBoard_deletedHousehold_isNotFound() {
        when(groupRepository.findByUidAndIsActiveTrue("grp-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getBoard("grp-1", new TaskBoardFilterRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(taskRepository, never())
                .findAll(ArgumentMatchers.<Specification<Task>>any(), any(PageRequest.class));
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
        r.setTagNames(List.of("Kitchen"));
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.of(task()));
        when(groupMemberRepository.findByUid("gm-6")).thenReturn(Optional.of(member(6L)));
        resolvesTo(tag(7L, "Kitchen"));
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(
                Category.builder().idCategory(3L).isActive(true).group(group()).build()));
        savesWhatItIsGiven();

        taskService.updateTask("tsk-1", r);

        // Every field this endpoint can change, in the order it applies them. The household, the
        // author and the status are absent because an edit cannot touch any of them, and the
        // recurrence pattern because it is re-derived rather than tracked.
        assertThat(capturedChanges().fields()).containsExactly(
                "name", "description", "priority", "recurrenceType", "nextDueDate",
                "requiresPhotoProof", "estimatedTimeMinutes", "assignedToUid", "categoryId",
                "tagNames");
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

    @Test
    void getTaskByUid_carriesItsTags() {
        when(taskRepository.findDetailedByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.of(task()));
        taskAlreadyCarries(tag(7L, "Kitchen"));

        // The finder answers with live tags only, which is what keeps a deleted label off the
        // tasks that still have its join rows.
        assertThat(taskService.getTaskByUid("tsk-1").getTags())
                .extracting("name").containsExactly("Kitchen");
    }

    @Test
    void createTask_withTags_attachesEachOne() {
        callerIsAMember();
        savesWhatItIsGiven();
        TaskCreateRequest r = createRequest();
        r.setTagNames(List.of("Kitchen"));
        resolvesTo(tag(7L, "Kitchen"));

        taskService.createTask(r, CALLER_USER_ID);

        assertThat(savedLink().getTag().getIdTag()).isEqualTo(7L);
        assertThat(savedLink().getTask()).isSameAs(savedTask());
    }

    @Test
    void updateTask_tagAdded_insertsOnlyTheNewJoinRow() {
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.of(task()));
        savesWhatItIsGiven();
        taskAlreadyCarries(tag(7L, "Kitchen"));
        resolvesTo(tag(7L, "Kitchen"), tag(8L, "Weekly"));

        taskService.updateTask("tsk-1", updateRequest());

        // The row for Kitchen is left exactly as it was: its date_created is the only record
        // of when the task was tagged.
        assertThat(savedLink().getTag().getIdTag()).isEqualTo(8L);
        verify(taskTagRepository, never()).deleteByTaskIdTaskAndTagIdTag(any(), any());
    }

    @Test
    void updateTask_tagRemoved_deletesOnlyThatJoinRow() {
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.of(task()));
        savesWhatItIsGiven();
        taskAlreadyCarries(tag(7L, "Kitchen"), tag(8L, "Weekly"));
        resolvesTo(tag(7L, "Kitchen"));

        taskService.updateTask("tsk-1", updateRequest());

        verify(taskTagRepository).deleteByTaskIdTaskAndTagIdTag(1L, 8L);
        verify(taskTagRepository, never()).save(any());
    }

    @Test
    void updateTask_tagsOmitted_takesThemAllOff() {
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.of(task()));
        savesWhatItIsGiven();
        taskAlreadyCarries(tag(7L, "Kitchen"));

        taskService.updateTask("tsk-1", updateRequest());

        // An absent field is the task having no tags, the same way an absent assignee hands it
        // back to the pool - this request is the whole state, not a patch of it.
        verify(taskTagRepository).deleteByTaskIdTaskAndTagIdTag(1L, 7L);
        assertThat(capturedChanges().fields()).contains("tagNames");
    }

    @Test
    void updateTask_sameTagsInAnotherOrder_writesNoChange() {
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.of(task()));
        savesWhatItIsGiven();
        taskAlreadyCarries(tag(7L, "Kitchen"), tag(8L, "Weekly"));
        resolvesTo(tag(8L, "Weekly"), tag(7L, "Kitchen"));

        taskService.updateTask("tsk-1", updateRequest());

        assertThat(capturedChanges().fields()).doesNotContain("tagNames");
        verify(taskTagRepository, never()).save(any());
        verify(taskTagRepository, never()).deleteByTaskIdTaskAndTagIdTag(any(), any());
    }

    @Test
    void updateTask_tagsChanged_doesNotKeepWhichOnes() {
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.of(task()));
        savesWhatItIsGiven();
        resolvesTo(tag(7L, "Kitchen"));

        taskService.updateTask("tsk-1", updateRequest());

        // A tag is text a member typed, and a household trail is kept for good.
        assertThat(capturedChanges().entries()).contains(AuditFieldChange.withheld("tagNames"));
    }

    // ---------------------------------------------------------------- claiming and handing on

    private void choreIsLocked(Task task) {
        when(taskRepository.findForUpdateByUid("tsk-1")).thenReturn(Optional.of(task));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(CALLER_USER_ID, GROUP_ID))
                .thenReturn(Optional.of(member(AUTHOR_ID)));
    }

    private AssignTaskRequest assignTo(String memberUid) {
        AssignTaskRequest request = new AssignTaskRequest();
        request.setMemberUid(memberUid);
        return request;
    }

    @Test
    void claimTask_unassignedChore_becomesTheCallers() {
        choreIsLocked(task());
        savesWhatItIsGiven();

        taskService.claimTask("tsk-1", CALLER_USER_ID);

        assertThat(savedTask().getAssignedTo().getIdGroupMember()).isEqualTo(AUTHOR_ID);
        verify(auditLogService).recordEvent(eq(AuditEntityType.TASK), any(), eq(AuditAction.TASK_CLAIMED),
                any(), any(), any());
    }

    @Test
    void claimTask_choreSomebodyAlreadyHas_isRefused() {
        Task task = task();
        task.setAssignedTo(member(77L));
        choreIsLocked(task);

        // Handing a chore on is a different act with a different right behind it, so claiming must
        // not quietly do it.
        assertThatThrownBy(() -> taskService.claimTask("tsk-1", CALLER_USER_ID))
                .isInstanceOf(BusinessRuleException.class);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void claimTask_callerFromAnotherHousehold_isRefused() {
        when(taskRepository.findForUpdateByUid("tsk-1")).thenReturn(Optional.of(task()));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(CALLER_USER_ID, GROUP_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.claimTask("tsk-1", CALLER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void claimTask_always_locksTheRowFirst() {
        choreIsLocked(task());
        savesWhatItIsGiven();

        taskService.claimTask("tsk-1", CALLER_USER_ID);

        // Two people claiming at once would otherwise both read it as unassigned.
        verify(taskRepository).findForUpdateByUid("tsk-1");
        verify(taskRepository, never()).findByUidAndIsActiveTrue(any());
    }

    @Test
    void claimTask_deletedTask_isNotFound() {
        when(taskRepository.findForUpdateByUid("tsk-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.claimTask("tsk-1", CALLER_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void assignTask_aMemberOfTheHousehold_getsTheChore() {
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.of(task()));
        when(groupMemberRepository.findByUid("gm-5")).thenReturn(Optional.of(member(AUTHOR_ID)));
        savesWhatItIsGiven();

        taskService.assignTask("tsk-1", assignTo("gm-5"));

        assertThat(savedTask().getAssignedTo().getIdGroupMember()).isEqualTo(AUTHOR_ID);
        verify(auditLogService).recordEvent(eq(AuditEntityType.TASK), any(), eq(AuditAction.TASK_ASSIGNED),
                any(), any(), any());
    }

    @Test
    void assignTask_noMemberNamed_releasesItBackToThePool() {
        Task task = task();
        task.setAssignedTo(member(AUTHOR_ID));
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.of(task));
        savesWhatItIsGiven();

        taskService.assignTask("tsk-1", assignTo(null));

        // The only way to put a chore down without giving it to somebody else.
        assertThat(savedTask().getAssignedTo()).isNull();
    }

    @Test
    void assignTask_memberOfAnotherHousehold_isNotFound() {
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.of(task()));
        when(groupMemberRepository.findByUid("gm-outside")).thenReturn(Optional.of(
                member(77L, Group.builder().idGroup(999L).build())));

        // A reference into another household is as absent as one that never existed.
        assertThatThrownBy(() -> taskService.assignTask("tsk-1", assignTo("gm-outside")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void assignTask_deletedTask_isNotFound() {
        when(taskRepository.findByUidAndIsActiveTrue("tsk-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.assignTask("tsk-1", assignTo("gm-5")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
