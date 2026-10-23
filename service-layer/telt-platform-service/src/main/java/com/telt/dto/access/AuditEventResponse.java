package com.telt.dto.access;

import com.telt.entity.audit.AuditEvent;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(UUID eventId, UUID tenantId, String actor, String action, String entityType,
                                 String entityId, String details, String correlationId, Instant occurredAt) {

    public static AuditEventResponse from(AuditEvent e) {
        return new AuditEventResponse(e.getEventId(), e.getTenantId(), e.getActor(), e.getAction(), e.getEntityType(),
                e.getEntityId(), e.getDetails(), e.getCorrelationId(), e.getOccurredAt());
    }
}
