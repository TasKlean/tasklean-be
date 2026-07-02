package com.tasklean.api.domain.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    List<Device> findByUserIdUserAndIsActiveTrue(Long userId);

    Optional<Device> findByDeviceToken(String deviceToken);

    boolean existsByDeviceToken(String deviceToken);
}
