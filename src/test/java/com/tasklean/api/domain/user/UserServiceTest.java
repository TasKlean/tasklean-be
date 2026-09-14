package com.tasklean.api.domain.user;

import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private UserService userService;

    @Test
    void updateUserRole_changesRoleAndAudits() {
        User user = User.builder().idUser(1L).uid("usr-x").role(UserRole.USER).build();
        when(userRepository.findByUid("usr-x")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateUserRole("usr-x", UserRole.ADMIN);

        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(response.getRole()).isEqualTo(UserRole.ADMIN);
        verify(auditLogService).recordEvent(eq(AuditEntityType.USER), eq(1L),
                eq(AuditAction.ROLE_CHANGED), anyString(), isNull());
    }

    @Test
    void updateUserRole_unknownUid_throwsNotFound() {
        when(userRepository.findByUid("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUserRole("nope", UserRole.ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
