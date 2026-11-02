package com.tasklean.api.domain.upload;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Data access for uploads, including the query and the single-row update the orphan sweep runs on.
 */
@Repository
public interface UploadRepository extends JpaRepository<Upload, Long> {

    Optional<Upload> findByUid(String uid);

    /**
     * Finds uploads whose object nothing points at: one signed for but never attached, and one that
     * was replaced and is past its grace. Oldest first, so a backlog drains in order.
     *
     * @param pendingBefore    cutoff for a never-attached upload, by when it was signed for
     * @param supersededBefore cutoff for a replaced upload, by when it was replaced
     * @param pageable         the cap on one run, so a backlog costs many small runs not one huge one
     * @return the rows to collect
     */
    @Query("SELECT u FROM Upload u WHERE "
            + "(u.status = com.tasklean.api.domain.upload.UploadStatus.PENDING "
            + "AND u.dateCreated < :pendingBefore) OR "
            + "(u.status = com.tasklean.api.domain.upload.UploadStatus.SUPERSEDED "
            + "AND u.dateUpdated < :supersededBefore) "
            + "ORDER BY u.dateCreated")
    List<Upload> findCollectable(LocalDateTime pendingBefore, LocalDateTime supersededBefore,
                                 Pageable pageable);

    /**
     * Records that an upload's object is gone from the bucket. Transactional in its own right: the
     * sweep deliberately commits one row at a time, so a failure costs one object, not the run.
     *
     * @param id the upload's internal id
     */
    @Transactional
    @Modifying
    @Query("UPDATE Upload u SET u.status = com.tasklean.api.domain.upload.UploadStatus.DELETED "
            + "WHERE u.idUpload = :id")
    void markDeleted(Long id);
}
