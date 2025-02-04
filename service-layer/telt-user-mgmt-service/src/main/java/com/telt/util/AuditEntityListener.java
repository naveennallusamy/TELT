package com.telt.util;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

public class AuditEntityListener {

    @PrePersist
    public void setCreatedBy(AuditableEntity entity) {
        // Extract the username (or user ID) from SecurityContextHolder
        String createdBy = getAuthenticatedUsername();
        entity.setCreatedOn(new java.util.Date());
        entity.setCreatedBy(createdBy); // Set the value for createdBy field
    }

    @PreUpdate
    public void setUpdatedBy(AuditableEntity entity) {
        String username = getAuthenticatedUsername();
        entity.setUpdatedOn(new java.util.Date());
        entity.setUpdatedBy(username);
    }

    private String getAuthenticatedUsername() {
        // Check if a user is authenticated
        if (SecurityContextHolder.getContext().getAuthentication() != null &&
                SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {

            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof UserDetails) {
                return ((UserDetails) principal).getUsername(); // Extract user name or ID
            } else {
                return principal.toString(); // Fallback for simple usernames
            }
        }

        return "SYSTEM"; // Default value for non-authenticated users (e.g., system jobs)
    }
}