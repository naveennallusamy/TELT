package com.telt.entity.question;

import com.telt.constants.GrantStatus;
import com.telt.constants.ReleasePolicy;
import com.telt.util.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only use of a platform bank by one tenant ({@code tenantId} is the grantee).
 */
@Entity
@Getter
@Setter
@Table(name = "platform_bank_grants")
public class PlatformBankGrant extends AuditableEntity {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID grantId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID bankId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReleasePolicy releasePolicy;

    private UUID releaseId;

    @Column(nullable = false)
    private boolean allowClone;

    @Column(nullable = false)
    private boolean answerKeysVisible;

    private Instant validFrom;

    private Instant validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GrantStatus status = GrantStatus.ACTIVE;

    private Instant revokedAt;

    public boolean isUsable(Instant now) {
        return status == GrantStatus.ACTIVE && (validFrom == null || !validFrom.isAfter(now))
                && (validUntil == null || validUntil.isAfter(now));
    }
}
