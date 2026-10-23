package com.telt.entity.tenant;

import com.telt.constants.RecordStatus;
import com.telt.util.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/**
 * Node of a tenant's department tree. {@code path} is the slash-separated chain of ancestor ids ending with this
 * department's id (e.g. {@code /root-id/child-id/}), so descendants share its prefix.
 */
@Entity
@Getter
@Setter
@Table(name = "departments")
public class Department extends AuditableEntity {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID departmentId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(updatable = false)
    private UUID parentDepartmentId;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordStatus status = RecordStatus.ACTIVE;
}
