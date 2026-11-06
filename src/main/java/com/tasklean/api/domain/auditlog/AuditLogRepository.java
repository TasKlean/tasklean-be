package com.tasklean.api.domain.auditlog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Data access for the audit trail. Reads go through specifications rather than declared finders:
 * a feed is a scope, a visibility rule, an optional filter and a cursor composed per request, and
 * spelling every combination out as its own query is what the specification seam avoids.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
}
