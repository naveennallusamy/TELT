package com.telt.util;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;

public class AuditEntityListener {

    private static final String SYSTEM = "SYSTEM";

    @PrePersist
    public void onCreate(AuditableEntity entity) {
        Instant now = Instant.now();
        String actor = currentActor();
        entity.setCreatedOn(now);
        entity.setCreatedBy(actor);
        entity.setUpdatedOn(now);
        entity.setUpdatedBy(actor);
    }

    @PreUpdate
    public void onUpdate(AuditableEntity entity) {
        entity.setUpdatedOn(Instant.now());
        entity.setUpdatedBy(currentActor());
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return SYSTEM;
        }
        return authentication.getName();
    }
}
