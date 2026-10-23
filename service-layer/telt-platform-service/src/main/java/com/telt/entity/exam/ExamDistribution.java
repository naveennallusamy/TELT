package com.telt.entity.exam;

import com.telt.constants.GrantStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A platform exam distributed to one tenant; {@code examId} is the tenant's read-only copy.
 */
@Entity
@Getter
@Setter
@Table(name = "exam_distributions")
public class ExamDistribution {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID distributionId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID sourceExamId;

    @Column(nullable = false)
    private int sourceVersionNo;

    @Column(nullable = false, updatable = false)
    private UUID examId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private OverridePolicy overridePolicy;

    @Column(nullable = false)
    private boolean answerKeysVisible;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GrantStatus status = GrantStatus.ACTIVE;

    @Column(nullable = false)
    private UUID distributedBy;

    @Column(nullable = false)
    private Instant distributedAt;

    private Instant revokedAt;

    @Version
    @Column(nullable = false)
    private long rowVersion;
}
