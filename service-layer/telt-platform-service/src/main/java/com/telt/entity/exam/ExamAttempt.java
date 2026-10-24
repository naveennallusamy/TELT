package com.telt.entity.exam;

import com.telt.constants.AttemptStatus;
import com.telt.constants.GradingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One sitting of a schedule by a student. {@code settings} are the schedule's effective settings at start.
 */
@Entity
@Getter
@Setter
@Table(name = "exam_attempts")
public class ExamAttempt {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID attemptId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID scheduleId;

    @Column(nullable = false, updatable = false)
    private UUID examId;

    @Column(nullable = false, updatable = false)
    private int versionNo;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private ExamSettings settings;

    @Column(nullable = false, updatable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant deadlineAt;

    @Column(nullable = false)
    private int currentSection;

    @Column(nullable = false)
    private Instant sectionStartedAt;

    @Column(nullable = false)
    private int extraMinutes;

    private Instant submittedAt;

    private BigDecimal score;

    @Column(nullable = false, updatable = false)
    private BigDecimal maxScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GradingStatus gradingStatus = GradingStatus.NOT_GRADED;

    @Version
    @Column(nullable = false)
    private long rowVersion;
}
