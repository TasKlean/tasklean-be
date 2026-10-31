package com.tasklean.api.domain.upload;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UploadRepository extends JpaRepository<Upload, Long> {

    Optional<Upload> findByUid(String uid);
}
