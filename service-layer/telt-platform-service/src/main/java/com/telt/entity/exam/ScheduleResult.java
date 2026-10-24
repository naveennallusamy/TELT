package com.telt.entity.exam;

import com.telt.constants.ResultStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Approval state of a manually released schedule's results: submitted, then approved or rejected, then published.
 */
@Entity
@Getter
@Setter
@Table(name = "schedule_results")
public class ScheduleResult {

    @Id
    private UUID scheduleId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResultStatus status;

    @Column(nullable = false)
    private UUID submittedBy;

    @Column(nullable = false)
    private Instant submittedAt;

    private UUID decidedBy;
    private Instant decidedAt;
    private String comment;
    private UUID publishedBy;
    private Instant publishedAt;

    @Version
    @Column(nullable = false)
    private long rowVersion;
}
