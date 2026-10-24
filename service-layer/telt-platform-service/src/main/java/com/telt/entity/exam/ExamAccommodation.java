package com.telt.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Extra time for one student on one schedule; it extends both the attempt duration and the window end for them.
 */
@Entity
@Getter
@Setter
@Table(name = "exam_accommodations")
public class ExamAccommodation {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID accommodationId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID scheduleId;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private int extraMinutes;

    private String reason;

    @Column(nullable = false)
    private UUID grantedBy;

    @Column(nullable = false)
    private Instant grantedAt;
}
