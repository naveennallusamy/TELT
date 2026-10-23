package com.telt.util;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditEntityListener.class)
public abstract class AuditableEntity {

    @Column(updatable = false)
    private String createdBy;

    @Column(updatable = false)
    private Instant createdOn;

    private Instant updatedOn;

    private String updatedBy;
}
