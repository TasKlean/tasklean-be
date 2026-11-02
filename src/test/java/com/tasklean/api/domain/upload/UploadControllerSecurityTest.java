package com.tasklean.api.domain.upload;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.groupmember.GroupRole;
import com.tasklean.api.domain.upload.dto.SignedUrlResponse;
import com.tasklean.api.domain.upload.dto.UploadResponse;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRole;
import com.tasklean.api.security.GroupSecurity;
import com.tasklean.api.security.MethodSecuritySliceConfig;
import com.tasklean.api.security.UploadSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the @PreAuthorize wiring on {@link UploadController}. An image is authorized through the
 * thing it belongs to, so UploadSecurity and GroupSecurity are both real here, with their
 * repositories mocked to place the caller.
 */
@WebMvcTest(UploadController.class)
@Import({MethodSecuritySliceConfig.class, UploadSecurity.class, GroupSecurity.class})
class UploadControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UploadService uploadService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private GroupRepository groupRepository;

    @MockitoBean
    private GroupMemberRepository groupMemberRepository;

    @MockitoBean
    private UploadRepository uploadRepository;

    private static final String GROUP_UID = "grp-1";
    private static final String UPLOAD_UID = "upl-1";
    private static final Long GROUP_ID = 10L;
    private static final Long USER_ID = 1L;

    private static final String BODY = """
            {"purpose":"GROUP_PHOTO","targetUid":"grp-1","contentType":"image/png","sizeBytes":1024}""";

    private RequestPostProcessor as(UserRole platformRole) {
        AuthPrincipal principal = new AuthPrincipal(USER_ID, "usr-1", "user@example.com", platformRole);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + platformRole.name()))));
    }

    // Places the caller in the target group with the given role.
    private void callerIsMemberWithRole(GroupRole role) {
        Group group = Group.builder().idGroup(GROUP_ID).uid(GROUP_UID).build();
        when(groupRepository.findByUid(GROUP_UID)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID))
                .thenReturn(Optional.of(GroupMember.builder()
                        .role(role).isActive(true).group(group)
                        .user(User.builder().idUser(USER_ID).build())
                        .build()));
    }

    private void uploadExists(UploadStatus status, Long requesterId) {
        when(uploadRepository.findByUid(UPLOAD_UID)).thenReturn(Optional.of(Upload.builder()
                .uid(UPLOAD_UID).status(status)
                .user(User.builder().idUser(requesterId).build())
                .group(Group.builder().idGroup(GROUP_ID).uid(GROUP_UID).build())
                .build()));
    }

    private void serviceAnswers() {
        when(uploadService.createUpload(any(), anyLong()))
                .thenReturn(UploadResponse.builder().uid(UPLOAD_UID).build());
        when(uploadService.downloadUrl(UPLOAD_UID))
                .thenReturn(SignedUrlResponse.builder().url("https://bucket/get").build());
    }

    // --- createUpload: the target group's admin, or platform SUPER_ADMIN ---

    @Test
    void createUpload_groupAdmin_created() throws Exception {
        callerIsMemberWithRole(GroupRole.GROUP_ADMIN);
        serviceAnswers();

        mockMvc.perform(post("/api/uploads").with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void createUpload_plainMemberOfThatGroup_forbidden() throws Exception {
        // Changing the household picture is a group-admin act, so asking for the URL is too.
        callerIsMemberWithRole(GroupRole.GROUP_MEMBER);

        mockMvc.perform(post("/api/uploads").with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUpload_callerNotInThatGroup_forbidden() throws Exception {
        mockMvc.perform(post("/api/uploads").with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUpload_platformAdmin_forbidden() throws Exception {
        // ADMIN is read-only across the platform, and a signature is a write.
        mockMvc.perform(post("/api/uploads").with(as(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUpload_superAdmin_created() throws Exception {
        serviceAnswers();

        mockMvc.perform(post("/api/uploads").with(as(UserRole.SUPER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    // --- createUpload: what is refused before any signature exists ---

    @Test
    void createUpload_unsupportedContentType_badRequest() throws Exception {
        mockMvc.perform(post("/api/uploads").with(as(UserRole.SUPER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purpose":"GROUP_PHOTO","targetUid":"grp-1",\
                                "contentType":"image/svg+xml","sizeBytes":1024}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUpload_overTheSizeCap_badRequest() throws Exception {
        mockMvc.perform(post("/api/uploads").with(as(UserRole.SUPER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purpose":"GROUP_PHOTO","targetUid":"grp-1",\
                                "contentType":"image/png","sizeBytes":99999999}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUpload_missingTarget_badRequest() throws Exception {
        mockMvc.perform(post("/api/uploads").with(as(UserRole.SUPER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purpose":"GROUP_PHOTO","contentType":"image/png","sizeBytes":1024}"""))
                .andExpect(status().isBadRequest());
    }

    // --- getUrl: anyone who may see the group, or the requester while it is still pending ---

    @Test
    void getUrl_memberOfTheGroup_ok() throws Exception {
        uploadExists(UploadStatus.ATTACHED, 2L);
        callerIsMemberWithRole(GroupRole.GROUP_MEMBER);
        serviceAnswers();

        mockMvc.perform(get("/api/uploads/{uid}", UPLOAD_UID).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void getUrl_callerOutsideTheGroup_forbidden() throws Exception {
        uploadExists(UploadStatus.ATTACHED, 2L);

        mockMvc.perform(get("/api/uploads/{uid}", UPLOAD_UID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUrl_pendingUploadOfAnotherUser_forbidden() throws Exception {
        // Not attached to anything yet, so group membership cannot stand in for ownership.
        uploadExists(UploadStatus.PENDING, 2L);
        callerIsMemberWithRole(GroupRole.GROUP_ADMIN);

        mockMvc.perform(get("/api/uploads/{uid}", UPLOAD_UID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUrl_ownPendingUpload_ok() throws Exception {
        uploadExists(UploadStatus.PENDING, USER_ID);
        serviceAnswers();

        mockMvc.perform(get("/api/uploads/{uid}", UPLOAD_UID).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void getUrl_unknownUpload_forbidden() throws Exception {
        // Nothing to resolve a group from, so the check cannot pass - a uid is not a capability.
        mockMvc.perform(get("/api/uploads/{uid}", UPLOAD_UID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUrl_platformAdmin_ok() throws Exception {
        serviceAnswers();

        mockMvc.perform(get("/api/uploads/{uid}", UPLOAD_UID).with(as(UserRole.ADMIN)))
                .andExpect(status().isOk());
    }
}
