package com.tasklean.api.domain.group;

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

import static org.assertj.core.api.Assertions.assertThat;
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
}
