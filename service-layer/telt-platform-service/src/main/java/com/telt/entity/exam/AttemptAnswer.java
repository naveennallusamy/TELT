package com.telt.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A question in an attempt, at the position the student sees it, with the student's option order and answer.
 */
@Entity
@Getter
@Setter
@Table(name = "attempt_answers")
public class AttemptAnswer {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID answerId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID attemptId;

    @Column(nullable = false, updatable = false)
    private UUID snapshotId;

    @Column(nullable = false, updatable = false)
    private int sectionIndex;

    @Column(nullable = false, updatable = false)
    private int position;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    private List<String> optionOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    private AnswerResponse response;

    private Instant savedAt;
    private BigDecimal marksAwarded;

    @Column(nullable = false)
    private boolean autoGraded;

    private UUID gradedBy;
    private Instant gradedAt;
    private String feedback;
}
