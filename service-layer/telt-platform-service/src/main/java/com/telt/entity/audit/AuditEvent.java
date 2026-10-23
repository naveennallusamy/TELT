package com.telt.entity.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record of a privileged action.
 */
@Entity
@Getter
@Setter
@Table(name = "audit_events")
public class AuditEvent {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID eventId;

    @Column(updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private String actor;

    @Column(nullable = false, updatable = false)
    private String action;

    @Column(nullable = false, updatable = false)
    private String entityType;

    @Column(updatable = false)
    private String entityId;

    @Column(updatable = false, length = 4000)
    private String details;

    @Column(updatable = false)
    private String correlationId;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;
}
