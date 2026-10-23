package com.telt.service;

import com.telt.config.CorrelationIdFilter;
import com.telt.entity.audit.AuditEvent;
import com.telt.repository.AuditEventRepository;
import com.telt.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Writes audit events in the caller's transaction, so an action and its audit record commit together.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    @Transactional
    public void record(UUID tenantId, String action, String entityType, Object entityId, Map<String, ?> details) {
        AuditEvent event = new AuditEvent();
        event.setTenantId(tenantId);
        event.setActor(TenantContext.getCurrentUserId().map(UUID::toString).orElse("SYSTEM"));
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId == null ? null : entityId.toString());
        String text = details.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        event.setDetails(text.length() > 4000 ? text.substring(0, 4000) : text);
        event.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
        event.setOccurredAt(Instant.now());
        auditEventRepository.save(event);
    }
}
