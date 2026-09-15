package com.tasklean.api.domain.group;

import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.group.dto.GroupRequest;
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
        return Group.builder().idGroup(10L).uid("grp-1").name("Home").isActive(true).build();
    }

    private GroupRequest updateRequest() {
        GroupRequest r = new GroupRequest();
        r.setName("Renamed");
        r.setDescription("desc");
        return r;
    }

    @Test
    void getGroupByUid_found_returns() {
        when(groupRepository.findByUid("grp-1")).thenReturn(Optional.of(group()));
        assertThat(groupService.getGroupByUid("grp-1").getUid()).isEqualTo("grp-1");
    }

    @Test
    void getGroupByUid_missing_throws() {
        when(groupRepository.findByUid("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> groupService.getGroupByUid("nope")).isInstanceOf(ResourceNotFoundException.class);
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
