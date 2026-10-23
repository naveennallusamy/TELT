package com.telt.dto.exam;

import com.telt.constants.QuestionType;
import com.telt.entity.exam.ExamQuestionSnapshot;
import com.telt.entity.question.AnswerKey;
import com.telt.entity.question.QuestionContent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code answerKey} and {@code explanation} are null unless the caller may see answer keys.
 */
public record SnapshotResponse(UUID itemId, UUID sectionId, int position, QuestionType type, QuestionContent content,
                               AnswerKey answerKey, String explanation, BigDecimal marks, BigDecimal negativeMarks,
                               UUID sourceQuestionId, int sourceVersionNo) {

    public static SnapshotResponse from(ExamQuestionSnapshot s, boolean canSeeKeys) {
        boolean withAnswerKey = canSeeKeys && s.isAnswerKeyVisible();
        return new SnapshotResponse(s.getItemId(), s.getSectionId(), s.getPosition(), s.getQuestionType(),
                s.getContent(), withAnswerKey ? s.getAnswerKey() : null, withAnswerKey ? s.getExplanation() : null,
                s.getMarks(), s.getNegativeMarks(), s.getSourceQuestionId(), s.getSourceVersionNo());
    }
}
