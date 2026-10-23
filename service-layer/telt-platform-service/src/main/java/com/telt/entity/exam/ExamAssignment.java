package com.telt.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Assigns a schedule to one student ({@code userId}) or to a department and its sub-departments.
 */
@Entity
@Getter
@Setter
@Table(name = "exam_assignments")
public class ExamAssignment {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID assignmentId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID scheduleId;

    @Column(updatable = false)
    private UUID userId;

    @Column(updatable = false)
    private UUID departmentId;

    @Column(nullable = false, updatable = false)
    private UUID assignedBy;

    @Column(nullable = false, updatable = false)
    private Instant assignedAt;
}
