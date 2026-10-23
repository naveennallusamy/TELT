package com.telt.entity.role;

import com.telt.util.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import com.telt.constants.Permission;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Seeded role: a named bundle of permissions. Roles are defined by Flyway and cannot be created through the API.
 * Non-assignable roles ({@code SUPER_ADMIN}, {@code TENANT_ADMIN}, {@code STUDENT}) are implied by account type.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role extends AuditableEntity {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID roleId;

    @Column(nullable = false, unique = true, updatable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private boolean assignable;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_code", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Permission> permissions = new HashSet<>();

    public Role(String name) {
        this.name = name;
    }
}
