package com.tasklean.api.domain.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdUserOrderByDateCreatedDesc(Long userId);

    List<Notification> findByUserIdUserAndIsReadFalseOrderByDateCreatedDesc(Long userId);

    long countByUserIdUserAndIsReadFalse(Long userId);
}
