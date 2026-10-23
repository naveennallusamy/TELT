package com.telt.entity.question;

import com.telt.constants.Difficulty;
import com.telt.constants.QuestionVersionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Immutable once it leaves {@code DRAFT} (enforced by a database trigger as well as the service).
 */
@Entity
@Getter
@Setter
@Table(name = "question_versions")
public class QuestionVersion {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID versionId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID questionId;

    @Column(nullable = false, updatable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionVersionStatus status = QuestionVersionStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private QuestionContent content;

    @JdbcTypeCode(SqlTypes.JSON)
    private AnswerKey answerKey;

    private String explanation;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal defaultMarks = BigDecimal.ONE;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal negativeMarks = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private Difficulty difficulty;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> tags = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private UUID authoredBy;

    @Column(nullable = false, updatable = false)
    private Instant createdOn;

    private Instant submittedAt;
    private UUID reviewedBy;
    private Instant reviewedAt;
}
