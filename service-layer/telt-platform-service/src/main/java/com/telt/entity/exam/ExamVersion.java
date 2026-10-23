package com.telt.entity.exam;

import com.telt.constants.ExamVersionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "exam_versions")
public class ExamVersion {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID versionId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID examId;

    @Column(nullable = false, updatable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExamVersionStatus status = ExamVersionStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private ExamSettings settings;

    private BigDecimal totalMarks;

    @Column(nullable = false, updatable = false)
    private UUID authoredBy;

    @Column(nullable = false, updatable = false)
    private Instant createdOn;

    private Instant submittedAt;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private UUID publishedBy;
    private Instant publishedAt;
}
