package com.tasklean.api.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Data access for users, including email, UID, and Google-sub lookups. */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUid(String uid);

    Optional<User> findByGoogleSub(String googleSub);

    boolean existsByEmail(String email);
}