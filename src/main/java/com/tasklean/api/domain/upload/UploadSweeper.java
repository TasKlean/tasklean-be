package com.tasklean.api.domain.upload;

import com.tasklean.api.common.storage.ObjectStorage;
import com.tasklean.api.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Deletes the images nothing points at: an upload signed for but never attached, and one that was
 * replaced. Separate from {@link UploadService} because the work is per-row I/O against the bucket
 * rather than one statement — each row commits on its own, so an unreachable object costs that
 * object and not the run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadSweeper {

    private final UploadRepository uploadRepository;
    private final ObjectStorage objectStorage;
    private final StorageProperties storageProperties;
    private final Clock clock;

    /** Scheduled cleanup — deletes the objects nothing points at any more, every 6 hours. */
    @Scheduled(fixedRate = 6 * 60 * 60 * 1000) // every 6 hours
    public void sweepOrphanedObjects() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Upload> collectable = uploadRepository.findCollectable(
                now.minus(storageProperties.getPendingGrace()),
                now.minus(storageProperties.getSupersededGrace()),
                PageRequest.of(0, storageProperties.getSweepBatch()));
        if (collectable.isEmpty()) {
            return;
        }

        int collected = 0;
        for (Upload upload : collectable) {
            if (collect(upload)) {
                collected++;
            }
        }
        log.info("Upload sweep: collected={} failed={}", collected, collectable.size() - collected);
    }

    // The object goes first: interrupted after the delete, the next run deletes an object that is
    // already gone, which the port treats as success. The other order would leak it for good.
    private boolean collect(Upload upload) {
        try {
            objectStorage.delete(upload.getStorageKey());
            uploadRepository.markDeleted(upload.getIdUpload());
            return true;
        } catch (RuntimeException ex) {
            // The row keeps its status, so a bucket that was briefly unreachable is retried.
            log.warn("Upload sweep failed for one object: uid={} status={}",
                    upload.getUid(), upload.getStatus(), ex);
            return false;
        }
    }
}
