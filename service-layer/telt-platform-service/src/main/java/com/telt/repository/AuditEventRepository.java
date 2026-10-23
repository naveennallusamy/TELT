package com.telt.repository;

import com.telt.entity.audit.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findTop200ByTenantIdOrderByOccurredAtDesc(UUID tenantId);

    List<AuditEvent> findTop200ByOrderByOccurredAtDesc();
}
