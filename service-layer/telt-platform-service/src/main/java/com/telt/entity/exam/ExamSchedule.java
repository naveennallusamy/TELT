package com.telt.entity.exam;

import com.telt.constants.ScheduleStatus;
import com.telt.util.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "exam_schedules")
public class ExamSchedule extends AuditableEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID scheduleId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID examId;

    @Column(nullable = false, updatable = false)
    private int versionNo;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private String timeZone;

    @Column(nullable = false)
    private int lateEntryMinutes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private ScheduleOverrides overrides = ScheduleOverrides.NONE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleStatus status = ScheduleStatus.SCHEDULED;

    @Version
    @Column(nullable = false)
    private long rowVersion;
}
