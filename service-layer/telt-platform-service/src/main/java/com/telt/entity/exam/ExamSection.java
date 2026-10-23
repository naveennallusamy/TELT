package com.telt.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "exam_sections")
public class ExamSection {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID sectionId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID examId;

    @Column(nullable = false, updatable = false)
    private int versionNo;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String title;

    private Integer timeLimitMinutes;
}
