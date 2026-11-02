package com.tasklean.api.domain.upload;

import com.tasklean.api.common.storage.ObjectStorage;
import com.tasklean.api.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadSweeperTest {

    @Mock
    private UploadRepository uploadRepository;

    @Mock
    private ObjectStorage objectStorage;

    // Not a mock: the grace periods it carries are what the cutoffs are computed from.
    private final StorageProperties storageProperties = new StorageProperties();

    private UploadSweeper uploadSweeper;

    @BeforeEach
    void setUp() {
        storageProperties.setPendingGrace(Duration.ofHours(24));
        storageProperties.setSupersededGrace(Duration.ofHours(1));
        storageProperties.setSweepBatch(200);
        uploadSweeper = new UploadSweeper(uploadRepository, objectStorage, storageProperties,
                Clock.systemUTC());
    }

    private Upload collectable(Long id, String key, UploadStatus status) {
        return Upload.builder().idUpload(id).uid("upload-" + id).storageKey(key).status(status).build();
    }

    private void stubCollectable(Upload... uploads) {
        when(uploadRepository.findCollectable(any(), any(), any())).thenReturn(List.of(uploads));
    }

    @Test
    void sweepOrphanedObjects_nothingToCollect_leavesTheBucketAlone() {
        when(uploadRepository.findCollectable(any(), any(), any())).thenReturn(List.of());

        uploadSweeper.sweepOrphanedObjects();

        verifyNoInteractions(objectStorage);
        verify(uploadRepository, never()).markDeleted(anyLong());
    }

    @Test
    void sweepOrphanedObjects_collectableRow_deletesTheObjectBeforeMarkingTheRow() {
        stubCollectable(collectable(1L, "group-photo/a.png", UploadStatus.PENDING));

        uploadSweeper.sweepOrphanedObjects();

        // Marking first would leak the object for good: nothing would ever look at the row again.
        InOrder order = inOrder(objectStorage, uploadRepository);
        order.verify(objectStorage).delete("group-photo/a.png");
        order.verify(uploadRepository).markDeleted(1L);
    }

    @Test
    void sweepOrphanedObjects_cutoffs_comeFromTheGracePeriods() {
        when(uploadRepository.findCollectable(any(), any(), any())).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);

        uploadSweeper.sweepOrphanedObjects();

        ArgumentCaptor<LocalDateTime> pending = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> superseded = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(uploadRepository).findCollectable(pending.capture(), superseded.capture(), any());
        assertThat(pending.getValue()).isBetween(before.minusHours(24).minusMinutes(1),
                before.minusHours(24).plusMinutes(1));
        assertThat(superseded.getValue()).isBetween(before.minusHours(1).minusMinutes(1),
                before.minusHours(1).plusMinutes(1));
    }

    @Test
    void sweepOrphanedObjects_asksForAtMostTheConfiguredBatch() {
        storageProperties.setSweepBatch(50);
        when(uploadRepository.findCollectable(any(), any(), any())).thenReturn(List.of());

        uploadSweeper.sweepOrphanedObjects();

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(uploadRepository).findCollectable(any(), any(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void sweepOrphanedObjects_oneUnreachableObject_stillCollectsTheRest() {
        stubCollectable(collectable(1L, "group-photo/bad.png", UploadStatus.PENDING),
                collectable(2L, "group-photo/good.png", UploadStatus.SUPERSEDED));
        doThrow(new RuntimeException("bucket down")).when(objectStorage).delete("group-photo/bad.png");

        uploadSweeper.sweepOrphanedObjects();

        verify(uploadRepository).markDeleted(2L);
        verify(uploadRepository, never()).markDeleted(1L);
    }

    @Test
    void sweepOrphanedObjects_deleteFails_rowKeepsItsStatusForTheNextRun() {
        stubCollectable(collectable(1L, "group-photo/a.png", UploadStatus.SUPERSEDED));
        doThrow(new RuntimeException("bucket down")).when(objectStorage).delete(anyString());

        uploadSweeper.sweepOrphanedObjects();

        verify(uploadRepository, never()).markDeleted(anyLong());
    }
}
