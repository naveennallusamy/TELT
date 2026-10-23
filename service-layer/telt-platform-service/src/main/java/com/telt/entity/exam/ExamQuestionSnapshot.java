package com.telt.entity.exam;

import com.telt.constants.QuestionType;
import com.telt.entity.question.AnswerKey;
import com.telt.entity.question.QuestionContent;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Immutable
@Getter
@Setter
@Table(name = "exam_question_snapshots")
public class ExamQuestionSnapshot {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID snapshotId;

    private UUID tenantId;
    private UUID examId;
    private int versionNo;
    private UUID sectionId;
    private UUID itemId;
    private int position;

    @Enumerated(EnumType.STRING)
    private QuestionType questionType;

    @JdbcTypeCode(SqlTypes.JSON)
    private QuestionContent content;

    @JdbcTypeCode(SqlTypes.JSON)
    private AnswerKey answerKey;

    private String explanation;
    private boolean answerKeyVisible;
    private BigDecimal marks;
    private BigDecimal negativeMarks;
    private UUID sourceTenantId;
    private UUID sourceQuestionId;
    private int sourceVersionNo;
}
