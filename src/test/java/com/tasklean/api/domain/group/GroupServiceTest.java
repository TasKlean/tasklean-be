package com.tasklean.api.domain.group;

import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.group.dto.GroupRequest;
import com.tasklean.api.domain.group.dto.MyGroupResponse;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.groupmember.GroupRole;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    @Spy
    private Clock clock = Clock.systemUTC();

    @InjectMocks
    private GroupService groupService;

    @Test
    void createGroup_makesCreatorFirstGroupAdmin() {
        GroupRequest request = new GroupRequest();
        request.setName("Home");

        when(groupRepository.existsByInviteCode(any())).thenReturn(false);
        when(groupRepository.save(any(Group.class))).thenAnswer(i -> {
            Group g = i.getArgument(0);
            g.setIdGroup(10L);
            return g;
        });
        User creatorRef = User.builder().idUser(1L).build();
        when(userRepository.getReferenceById(1L)).thenReturn(creatorRef);
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(i -> i.getArgument(0));

        groupService.createGroup(request, 1L);

        ArgumentCaptor<GroupMember> captor = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(captor.capture());
        GroupMember created = captor.getValue();
        assertThat(created.getRole()).isEqualTo(GroupRole.GROUP_ADMIN);
        assertThat(created.getUser()).isEqualTo(creatorRef);
        assertThat(created.getIsActive()).isTrue();
        assertThat(created.getDateJoined()).isNotNull();
    }

    private Group group() {
        return Group.builder().idGroup(10L).uid("grp-1").name("Home")
                .inviteCode("JOIN1234").isActive(true).build();
    }

    private GroupRequest updateRequest() {
        GroupRequest r = new GroupRequest();
        r.setName("Renamed");
        r.setDescription("desc");
        return r;
    }

    private GroupMember membership(Group group, GroupRole role) {
        return GroupMember.builder()
                .idGroupMember(100L)
                .group(group)
                .user(User.builder().idUser(1L).build())
                .role(role)
                .isActive(true)
                .dateJoined(LocalDateTime.now(ZoneOffset.UTC))
                .build();
    }

    @Test
    void getGroupByUid_found_returns() {
        when(groupRepository.findByUid("grp-1")).thenReturn(Optional.of(group()));
        assertThat(groupService.getGroupByUid("grp-1", 1L).getUid()).isEqualTo("grp-1");
    }

    @Test
    void getGroupByUid_missing_throws() {
        when(groupRepository.findByUid("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> groupService.getGroupByUid("nope", 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getGroupByUid_asGroupAdmin_includesInviteCode() {
        Group g = group();
        when(groupRepository.findByUid("grp-1")).thenReturn(Optional.of(g));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(1L, 10L))
                .thenReturn(Optional.of(membership(g, GroupRole.GROUP_ADMIN)));

        assertThat(groupService.getGroupByUid("grp-1", 1L).getInviteCode()).isEqualTo("JOIN1234");
    }

    @Test
    void getGroupByUid_asPlainMember_hidesInviteCode() {
        Group g = group();
        when(groupRepository.findByUid("grp-1")).thenReturn(Optional.of(g));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(1L, 10L))
                .thenReturn(Optional.of(membership(g, GroupRole.GROUP_MEMBER)));

        assertThat(groupService.getGroupByUid("grp-1", 1L).getInviteCode()).isNull();
    }

    @Test
    void getMyGroups_returnsActiveGroupsWithCallerRole() {
        Group g = group();
        when(groupMemberRepository.findByUserIdUserAndIsActiveTrue(1L))
                .thenReturn(List.of(membership(g, GroupRole.GROUP_MEMBER)));

        List<MyGroupResponse> mine = groupService.getMyGroups(1L);

        assertThat(mine).singleElement().satisfies(m -> {
            assertThat(m.getUid()).isEqualTo("grp-1");
            assertThat(m.getMyRole()).isEqualTo(GroupRole.GROUP_MEMBER);
        });
    }

    @Test
    void getMyGroups_skipsSoftDeletedGroups() {
        Group deleted = group();
        deleted.setIsActive(false);
        when(groupMemberRepository.findByUserIdUserAndIsActiveTrue(1L))
                .thenReturn(List.of(membership(deleted, GroupRole.GROUP_ADMIN)));

        assertThat(groupService.getMyGroups(1L)).isEmpty();
    }

    @Test
    void getMyGroups_groupAdmin_includesInviteCodeToShare() {
        when(groupMemberRepository.findByUserIdUserAndIsActiveTrue(1L))
                .thenReturn(List.of(membership(group(), GroupRole.GROUP_ADMIN)));

        assertThat(groupService.getMyGroups(1L).getFirst().getInviteCode()).isEqualTo("JOIN1234");
    }

    @Test
    void getMyGroups_plainMember_hidesInviteCode() {
        when(groupMemberRepository.findByUserIdUserAndIsActiveTrue(1L))
                .thenReturn(List.of(membership(group(), GroupRole.GROUP_MEMBER)));

        assertThat(groupService.getMyGroups(1L).getFirst().getInviteCode()).isNull();
    }

    @Test
    void getAllGroups_returnsList() {
        when(groupRepository.findAll()).thenReturn(List.of(group()));
        assertThat(groupService.getAllGroups()).hasSize(1);
    }

    @Test
    void updateGroup_success() {
        Group existing = group();
        when(groupRepository.findByUid("grp-1")).thenReturn(Optional.of(existing));
        when(groupRepository.save(any(Group.class))).thenAnswer(i -> i.getArgument(0));

        groupService.updateGroup("grp-1", updateRequest());

        assertThat(existing.getName()).isEqualTo("Renamed");
        verify(auditLogService).recordEvent(any(), any(), any(), any(), any());
    }

    @Test
    void updateGroup_missing_throws() {
        when(groupRepository.findByUid("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> groupService.updateGroup("nope", updateRequest())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteGroup_success_softDeletes() {
        Group existing = group();
        when(groupRepository.findByUid("grp-1")).thenReturn(Optional.of(existing));

        groupService.deleteGroup("grp-1");

        assertThat(existing.getIsActive()).isFalse();
        verify(auditLogService).recordEvent(any(), any(), any(), any(), any());
    }

    @Test
    void deleteGroup_missing_throws() {
        when(groupRepository.findByUid("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> groupService.deleteGroup("nope")).isInstanceOf(ResourceNotFoundException.class);
    }
}
