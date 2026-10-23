package com.telt.entity.exam;

import com.telt.constants.ReviewDecision;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "exam_reviews")
public class ExamReview {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID reviewId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID examId;

    @Column(nullable = false, updatable = false)
    private int versionNo;

    @Column(nullable = false, updatable = false)
    private UUID reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ReviewDecision decision;

    @Column(updatable = false)
    private String comments;

    @Column(nullable = false, updatable = false)
    private Instant reviewedAt;
}
