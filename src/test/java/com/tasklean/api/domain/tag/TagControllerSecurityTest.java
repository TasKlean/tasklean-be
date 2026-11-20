package com.tasklean.api.domain.tag;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.groupmember.GroupRole;
import com.tasklean.api.domain.tag.dto.TagResponse;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRole;
import com.tasklean.api.security.GroupSecurity;
import com.tasklean.api.security.MethodSecuritySliceConfig;
import com.tasklean.api.security.TagSecurity;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the @PreAuthorize wiring on {@link TagController}. The split that matters: any member
 * may create a tag and set its color, while deleting one is the household admin's. A rename is
 * admitted here and refused by {@code TagService}, because it depends on the body - the rule
 * itself is proven in {@code TagServiceTest}.
 */
@WebMvcTest(TagController.class)
@Import({MethodSecuritySliceConfig.class, TagSecurity.class, GroupSecurity.class})
class TagControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TagService tagService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private TagRepository tagRepository;

    @MockitoBean
    private GroupRepository groupRepository;

    @MockitoBean
    private GroupMemberRepository groupMemberRepository;

    private static final String GROUP_UID = "grp-1";
    private static final Long GROUP_ID = 10L;
    private static final Long USER_ID = 1L;

    private static final String BODY = """
            {"name":"quick","color":"#2ECC71","groupUid":"grp-1"}
            """;

    private RequestPostProcessor as(UserRole platformRole) {
        AuthPrincipal principal = new AuthPrincipal(USER_ID, "usr-1", "user@example.com", platformRole);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + platformRole.name()))));
    }

    private Group group() {
        return Group.builder().idGroup(GROUP_ID).uid(GROUP_UID).isActive(true).build();
    }

    private void callerIs(GroupRole role) {
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID))
                .thenReturn(Optional.of(GroupMember.builder()
                        .idGroupMember(4L).role(role).isActive(true)
                        .group(group()).user(User.builder().idUser(USER_ID).build())
                        .build()));
    }

    private void householdNamedInTheBodyExists() {
        when(groupRepository.findByUid(GROUP_UID)).thenReturn(Optional.of(group()));
    }

    private void tagExists() {
        when(tagRepository.findById(5L))
                .thenReturn(Optional.of(Tag.builder().idTag(5L).group(group()).build()));
    }

    @Test
    void createTag_plainMember_created() throws Exception {
        // The point of the whole feature: a member labels their own chore without asking anyone.
        householdNamedInTheBodyExists();
        callerIs(GroupRole.GROUP_MEMBER);
        when(tagService.createTag(any())).thenReturn(new TagResponse());

        mockMvc.perform(post("/api/tags").with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void createTag_callerNotInThatHousehold_forbidden() throws Exception {
        householdNamedInTheBodyExists();

        mockMvc.perform(post("/api/tags").with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTag_plainMember_ok() throws Exception {
        tagExists();
        callerIs(GroupRole.GROUP_MEMBER);
        when(tagService.getTagById(5L)).thenReturn(new TagResponse());

        mockMvc.perform(get("/api/tags/{id}", 5L).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void getTag_callerNotInThatHousehold_forbidden() throws Exception {
        tagExists();

        mockMvc.perform(get("/api/tags/{id}", 5L).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateTag_plainMember_ok() throws Exception {
        // Admitted on membership alone: the color is theirs to set, and a rename in the same
        // body is what the service refuses.
        tagExists();
        callerIs(GroupRole.GROUP_MEMBER);
        when(tagService.updateTag(any(), any(), any())).thenReturn(new TagResponse());

        mockMvc.perform(put("/api/tags/{id}", 5L).with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void updateTag_outsider_forbidden() throws Exception {
        tagExists();

        mockMvc.perform(put("/api/tags/{id}", 5L).with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteTag_plainMember_forbidden() throws Exception {
        tagExists();
        callerIs(GroupRole.GROUP_MEMBER);

        mockMvc.perform(delete("/api/tags/{id}", 5L).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteTag_householdAdmin_ok() throws Exception {
        tagExists();
        callerIs(GroupRole.GROUP_ADMIN);

        mockMvc.perform(delete("/api/tags/{id}", 5L).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }
}
