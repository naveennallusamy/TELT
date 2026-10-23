package com.telt.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "exam_items")
public class ExamItem {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID itemId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID sectionId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, updatable = false)
    private UUID sourceTenantId;

    @Column(nullable = false, updatable = false)
    private UUID questionId;

    @Column(nullable = false, updatable = false)
    private int questionVersionNo;

    @Column(updatable = false)
    private UUID grantId;

    private BigDecimal marks;

    private BigDecimal negativeMarks;
}
