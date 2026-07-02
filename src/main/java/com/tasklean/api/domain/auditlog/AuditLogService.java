package com.tasklean.api.domain.auditlog;

import com.tasklean.api.domain.auditlog.dto.AuditLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public List<AuditLogResponse> getLogsByGroup(Long groupId) {
        return auditLogRepository.findByGroupIdGroupOrderByDateCreatedDesc(groupId).stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    public List<AuditLogResponse> getLogsByEntity(String entityType, Long entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByDateCreatedDesc(entityType, entityId).stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    public List<AuditLogResponse> getLogsByMember(Long groupMemberId) {
        return auditLogRepository.findByGroupMemberIdGroupMemberOrderByDateCreatedDesc(groupMemberId).stream()
                .map(AuditLogResponse::from)
                .toList();
    }
}
