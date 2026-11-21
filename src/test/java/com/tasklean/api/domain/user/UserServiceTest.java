package com.tasklean.api.domain.user;

import com.tasklean.api.auth.refresh.RefreshTokenService;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditChanges;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditFieldChange;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.groupmember.MembershipClosure;
import com.tasklean.api.domain.upload.UploadService;
import com.tasklean.api.domain.user.dto.UserRequest;
import com.tasklean.api.domain.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UploadService uploadService;

    @Mock
    private MembershipClosure membershipClosure;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private UserService userService;

    @Test
    void updateUserRole_changesRoleAndAudits() {
        User user = User.builder().idUser(1L).uid("usr-x").role(UserRole.USER).build();
        when(userRepository.findByUidAndIsActiveTrue("usr-x")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateUserRole("usr-x", UserRole.ADMIN);

        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(response.getRole()).isEqualTo(UserRole.ADMIN);
        ArgumentCaptor<AuditChanges> changes = ArgumentCaptor.forClass(AuditChanges.class);
        verify(auditLogService).recordEvent(eq(AuditEntityType.USER), eq(1L),
                eq(AuditAction.ROLE_CHANGED), anyString(), isNull(), changes.capture());
        assertThat(changes.getValue().entries())
                .containsExactly(new AuditFieldChange("role", "USER", "ADMIN"));
    }

    @Test
    void updateUserRole_closedAccount_throwsNotFound() {
        when(userRepository.findByUidAndIsActiveTrue("usr-x")).thenReturn(Optional.empty());

        // Reads stay permissive so staff can still see a closed account, but granting one a
        // platform role would hand privilege to something nobody can sign in to.
        assertThatThrownBy(() -> userService.updateUserRole("usr-x", UserRole.ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserRole_unknownUid_throwsNotFound() {
        when(userRepository.findByUidAndIsActiveTrue("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUserRole("nope", UserRole.ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getUserByUid_found_returns() {
        when(userRepository.findByUid("usr-x")).thenReturn(Optional.of(User.builder().idUser(1L).uid("usr-x").build()));
        assertThat(userService.getUserByUid("usr-x").getUid()).isEqualTo("usr-x");
    }

    @Test
    void getUserByUid_missing_throws() {
        when(userRepository.findByUid("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.getUserByUid("nope")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAllUsers_returnsList() {
        when(userRepository.findAll()).thenReturn(List.of(User.builder().idUser(1L).build()));
        assertThat(userService.getAllUsers()).hasSize(1);
    }

    @Test
    void updateUser_success_savesAndAudits() {
        User user = User.builder().idUser(1L).uid("usr-x").build();
        when(userRepository.findByUidAndIsActiveTrue("usr-x")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        UserRequest request = new UserRequest();
        request.setName("New");
        request.setLastName("Name");

        userService.updateUser("usr-x", request, 1L);

        assertThat(user.getName()).isEqualTo("New");
        ArgumentCaptor<AuditChanges> changes = ArgumentCaptor.forClass(AuditChanges.class);
        verify(auditLogService).recordEvent(eq(AuditEntityType.USER), eq(1L), eq(AuditAction.UPDATE),
                anyString(), isNull(), changes.capture());
        // middleName was absent in both, so only the two the request carried moved.
        assertThat(changes.getValue().fields()).containsExactly("name", "lastName");
        // A person's own names are never kept, however harmless they look.
        assertThat(changes.getValue().entries())
                .containsExactly(AuditFieldChange.withheld("name"), AuditFieldChange.withheld("lastName"));
    }

    @Test
    void updateUser_accountWithAPhoto_leavesItAlone() {
        User user = User.builder().idUser(1L).uid("usr-x")
                .photoUrl("https://lh3.googleusercontent.com/a/abc").build();
        when(userRepository.findByUidAndIsActiveTrue("usr-x")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        UserRequest request = new UserRequest();
        request.setName("New");
        request.setLastName("Name");

        userService.updateUser("usr-x", request, 1L);

        // A profile edit cannot reach the photo: the request has no field for it, so a caller
        // cannot point their picture at a host of their choosing.
        assertThat(user.getPhotoUrl()).isEqualTo("https://lh3.googleusercontent.com/a/abc");
        ArgumentCaptor<AuditChanges> changes = ArgumentCaptor.forClass(AuditChanges.class);
        verify(auditLogService).recordEvent(eq(AuditEntityType.USER), eq(1L), eq(AuditAction.UPDATE),
                anyString(), isNull(), changes.capture());
        assertThat(changes.getValue().fields()).doesNotContain("photoUrl");
    }

    @Test
    void deleteUser_success_softDeletesAndAudits() {
        User user = User.builder().idUser(1L).uid("usr-x").isActive(true).build();
        when(userRepository.findByUidAndIsActiveTrue("usr-x")).thenReturn(Optional.of(user));

        userService.deleteUser("usr-x", 99L);

        assertThat(user.getIsActive()).isFalse();
        verify(auditLogService).recordEvent(eq(AuditEntityType.USER), eq(1L), eq(AuditAction.ACCOUNT_DELETED), anyString(), isNull());
    }
    @Test
    void updateUser_self_attachesTheUploadAndTracksItByName() {
        User user = User.builder().idUser(1L).uid("usr-x").build();
        when(userRepository.findByUidAndIsActiveTrue("usr-x")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(uploadService.attachUserPhoto("up-1", user, 1L)).thenReturn("up-1");
        UserRequest request = namedRequest();
        request.setPhotoUid("up-1");

        userService.updateUser("usr-x", request, 1L);

        assertThat(user.getPhotoUid()).isEqualTo("up-1");
        ArgumentCaptor<AuditChanges> changes = ArgumentCaptor.forClass(AuditChanges.class);
        verify(auditLogService).recordEvent(eq(AuditEntityType.USER), eq(1L), eq(AuditAction.UPDATE),
                anyString(), isNull(), changes.capture());
        // The uid is a reference, not content, but names-only matches the group's photo field.
        assertThat(changes.getValue().entries()).contains(AuditFieldChange.withheld("photoUid"));
    }

    @Test
    void updateUser_selfSendingNoPhotoUid_clearsThePhoto() {
        User user = User.builder().idUser(1L).uid("usr-x").photoUid("up-old").build();
        when(userRepository.findByUidAndIsActiveTrue("usr-x")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(uploadService.attachUserPhoto(isNull(), eq(user), eq(1L))).thenReturn(null);

        userService.updateUser("usr-x", namedRequest(), 1L);

        // Same contract as a group's photo: no uid means no picture of ours, so Google's shows.
        assertThat(user.getPhotoUid()).isNull();
    }

    @Test
    void updateUser_superAdminSendingAPhotoUid_throwsAccessDenied() {
        User subject = User.builder().idUser(1L).uid("usr-x").build();
        when(userRepository.findByUidAndIsActiveTrue("usr-x")).thenReturn(Optional.of(subject));
        UserRequest request = namedRequest();
        request.setPhotoUid("up-1");

        // Nobody puts a face on somebody else's profile, whatever their platform role.
        assertThatThrownBy(() -> userService.updateUser("usr-x", request, 99L))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(uploadService);
    }

    @Test
    void updateUser_superAdminEditingNames_leavesThePhotoAlone() {
        User subject = User.builder().idUser(1L).uid("usr-x").photoUid("up-old").build();
        when(userRepository.findByUidAndIsActiveTrue("usr-x")).thenReturn(Optional.of(subject));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        userService.updateUser("usr-x", namedRequest(), 99L);

        // An absent field means "clear" for the subject, so an administrative name fix must not
        // read it that way - otherwise correcting a surname would wipe somebody's picture.
        assertThat(subject.getPhotoUid()).isEqualTo("up-old");
        verifyNoInteractions(uploadService);
        ArgumentCaptor<AuditChanges> changes = ArgumentCaptor.forClass(AuditChanges.class);
        verify(auditLogService).recordEvent(eq(AuditEntityType.USER), eq(1L), eq(AuditAction.UPDATE),
                anyString(), isNull(), changes.capture());
        assertThat(changes.getValue().fields()).doesNotContain("photoUid");
    }

    @Test
    void deleteUser_releasesThePhoto() {
        User user = User.builder().idUser(1L).uid("usr-x").isActive(true).photoUid("up-1").build();
        when(userRepository.findByUidAndIsActiveTrue("usr-x")).thenReturn(Optional.of(user));

        userService.deleteUser("usr-x", 99L);

        // Left attached, nothing would ever collect the object.
        verify(uploadService).releaseUserPhoto(user);
        assertThat(user.getPhotoUid()).isNull();
    }

    @Test
    void deleteUser_revokesEverySession() {
        User user = User.builder().idUser(1L).uid("usr-x").isActive(true).build();
        when(userRepository.findByUidAndIsActiveTrue("usr-x")).thenReturn(Optional.of(user));

        userService.deleteUser("usr-x", 99L);

        // Without this an admin-deleted account renews itself at the next refresh.
        verify(refreshTokenService).revokeAllUserTokens(user);
    }

    @Test
    void deleteUser_endsTheHouseholdMemberships() {
        User user = User.builder().idUser(1L).uid("usr-x").isActive(true).build();
        when(userRepository.findByUidAndIsActiveTrue("usr-x")).thenReturn(Optional.of(user));

        userService.deleteUser("usr-x", 99L);

        // The acting admin is passed on, not the subject: an admin who is also a member of one of
        // these households is the one attributed with ending the membership.
        verify(membershipClosure).closeFor(user, 99L);
    }

    @Test
    void deleteUser_alreadyDeleted_throwsNotFound() {
        // The row is deliberately reachable through the unfiltered finder: an unstubbed one answers
        // empty too, so without this the test would pass however the lookup was written.
        deletedButStillOnTheUnfilteredFinder();

        assertThatThrownBy(() -> userService.deleteUser("usr-x", 99L))
                .isInstanceOf(ResourceNotFoundException.class);
        // A second delete must not stamp a second ACCOUNT_DELETED on the trail, nor end
        // memberships that the first one already ended.
        verifyNoInteractions(auditLogService, refreshTokenService, membershipClosure);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_deletedAccount_throwsNotFound() {
        deletedButStillOnTheUnfilteredFinder();

        // A closed account is not an editable target, whoever is asking.
        assertThatThrownBy(() -> userService.updateUser("usr-x", namedRequest(), 1L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(uploadService, auditLogService);
    }

    @Test
    void getUserByUid_accountWithAnUpload_publishesTheUidAndNotGooglesUrl() {
        User user = User.builder().idUser(1L).uid("usr-x")
                .photoUid("up-1").photoUrl("https://lh3.googleusercontent.com/a/abc").build();
        when(userRepository.findByUid("usr-x")).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserByUid("usr-x");

        // Exactly one source is published, and ours is the one that wins.
        assertThat(response.getPhotoUid()).isEqualTo("up-1");
        assertThat(response.getPhotoUrl()).isNull();
    }

    @Test
    void getUserByUid_accountWithOnlyGooglesPicture_publishesThatUrl() {
        User user = User.builder().idUser(1L).uid("usr-x")
                .photoUrl("https://lh3.googleusercontent.com/a/abc").build();
        when(userRepository.findByUid("usr-x")).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserByUid("usr-x");

        assertThat(response.getPhotoUrl()).isEqualTo("https://lh3.googleusercontent.com/a/abc");
        assertThat(response.getPhotoUid()).isNull();
    }

    // A soft-deleted account as the two finders see it: still there unfiltered, gone filtered.
    // Lenient because the point of each test using it is that the unfiltered stub goes untouched.
    private void deletedButStillOnTheUnfilteredFinder() {
        User deleted = User.builder().idUser(1L).uid("usr-x").isActive(false).build();
        lenient().when(userRepository.findByUid("usr-x")).thenReturn(Optional.of(deleted));
        when(userRepository.findByUidAndIsActiveTrue("usr-x")).thenReturn(Optional.empty());
    }

    private UserRequest namedRequest() {
        UserRequest request = new UserRequest();
        request.setName("New");
        request.setLastName("Name");
        return request;
    }
}
