package com.telt.entity.tenant;

import com.telt.util.AuditableEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Entity
@Getter
@Setter
@Table(name = "tenant")
public class Tenant extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tenantId;

    @NotNull(message = "Tenant Name must not be null")
    @NotEmpty(message = "Tenant Name must not be empty")
    @Column(nullable = false, unique = true)
    private String tenantName;

    private String description;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tenant tenant = (Tenant) o;
        return tenantId != null && tenantId.equals(tenant.tenantId); // Compare only ID
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId); // Hash only ID
    }
}

