package com.tasklean.api.security;

import com.tasklean.api.domain.upload.Upload;
import com.tasklean.api.domain.upload.UploadStatus;
import com.tasklean.api.domain.upload.UploadPurpose;
import com.tasklean.api.domain.upload.UploadRepository;
import com.tasklean.api.domain.upload.dto.UploadRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import java.util.Optional;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    private Upload attachedWithNoTarget(UploadPurpose purpose) {
        return Upload.builder()
                .uid("upl-1").status(UploadStatus.ATTACHED).purpose(purpose)
                .userId(7L)
                .build();
    }

    @Test
    void mayRead_attachedPhotoWhoseTargetReferenceIsGone_isRefusedRatherThanOpenedToEveryone() {
        when(uploadRepository.findByUid("upl-1"))
                .thenReturn(Optional.of(attachedWithNoTarget(UploadPurpose.GROUP_PHOTO)));

        // The whole visibility rule is "as visible as the thing it hangs on". With nothing to hang
        // on, that has to resolve to nobody - an image whose owner vanished must not become public
        // to anyone holding its uid.
        assertThat(uploadSecurity.mayRead("upl-1")).isFalse();
        verifyNoInteractions(groupSecurity);
    }

    @Test
    void mayRead_attachedCompletionPhotoWithNoTask_isRefused() {
        when(uploadRepository.findByUid("upl-1"))
                .thenReturn(Optional.of(attachedWithNoTarget(UploadPurpose.COMPLETION_PHOTO)));

        assertThat(uploadSecurity.mayRead("upl-1")).isFalse();
        verifyNoInteractions(groupSecurity);
    }

    @Test
    void mayRead_uploadThatDoesNotExist_isRefused() {
        when(uploadRepository.findByUid("upl-1")).thenReturn(Optional.empty());

        assertThat(uploadSecurity.mayRead("upl-1")).isFalse();
    }
}
