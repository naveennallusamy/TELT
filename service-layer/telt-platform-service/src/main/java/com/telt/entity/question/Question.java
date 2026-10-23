package com.telt.entity.question;

import com.telt.constants.QuestionStatus;
import com.telt.constants.QuestionType;
import com.telt.util.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "questions")
public class Question extends AuditableEntity {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID questionId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID bankId;

    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, updatable = false)
    private QuestionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionStatus status = QuestionStatus.ACTIVE;

    @Column(nullable = false)
    private int latestVersion;

    private Integer approvedVersion;

    @Version
    @Column(nullable = false)
    private long rowVersion;
}
