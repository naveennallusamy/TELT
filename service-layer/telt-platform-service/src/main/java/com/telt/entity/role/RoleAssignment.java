package com.telt.entity.role;

import com.telt.constants.ResourceType;
import com.telt.constants.ScopeType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "role_assignments")
public class RoleAssignment {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID assignmentId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "role_id", nullable = false, updatable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ScopeType scopeType;

    @Column(updatable = false)
    private UUID departmentId;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private ResourceType resourceType;

    @Column(updatable = false)
    private UUID resourceId;

    @Column(nullable = false, updatable = false)
    private String grantedBy;

    @Column(nullable = false, updatable = false)
    private Instant grantedAt;
}
