package com.tasklean.api.security;

import com.tasklean.api.domain.upload.UploadPurpose;
import com.tasklean.api.domain.upload.UploadRepository;
import com.tasklean.api.domain.upload.dto.UploadRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The paths that matter are covered end to end by {@code UploadControllerSecurityTest}; what is
 * here is the half-built request, which only a direct call can present.
 */
@ExtendWith(MockitoExtension.class)
class UploadSecurityTest {

    @Mock
    private UploadRepository uploadRepository;

    @Mock
    private GroupSecurity groupSecurity;

    @InjectMocks
    private UploadSecurity uploadSecurity;

    private static UploadRequest request(UploadPurpose purpose, String targetUid) {
        UploadRequest request = new UploadRequest();
        request.setPurpose(purpose);
        request.setTargetUid(targetUid);
        return request;
    }

    @Test
    void mayCreate_requestMissingItsPurpose_isDeniedWithoutAskingAboutTheGroup() {
        // @PreAuthorize runs before @Valid, so an incomplete body reaches this bean rather than
        // being refused as a 400 first; without the guard the switch below would throw.
        assertThat(uploadSecurity.mayCreate(request(null, "group-uid"))).isFalse();
        verifyNoInteractions(groupSecurity);
    }

    @Test
    void mayCreate_requestMissingItsTarget_isDenied() {
        assertThat(uploadSecurity.mayCreate(request(UploadPurpose.GROUP_PHOTO, null))).isFalse();
        verifyNoInteractions(groupSecurity);
    }
}
