package com.telt.entity.tenant;

import com.telt.constants.AccessMode;
import com.telt.constants.MembershipType;
import com.telt.constants.RecordStatus;
import com.telt.entity.user.User;
import com.telt.util.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "tenant_memberships")
public class TenantMembership extends AuditableEntity {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID membershipId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipType membershipType;

    @Enumerated(EnumType.STRING)
    private AccessMode accessMode;

    private UUID departmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordStatus status = RecordStatus.ACTIVE;

    public boolean isActive() {
        return status == RecordStatus.ACTIVE;
    }
}
