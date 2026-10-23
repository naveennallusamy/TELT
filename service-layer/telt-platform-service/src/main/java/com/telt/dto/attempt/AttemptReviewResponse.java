package com.telt.dto.attempt;

import com.telt.constants.QuestionType;
import com.telt.entity.exam.AnswerResponse;
import com.telt.entity.question.AnswerKey;
import com.telt.entity.question.QuestionContent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Staff view of every answer in an attempt. {@code answerKey} is null unless the caller may see answer keys.
 */
public record AttemptReviewResponse(AttemptSummary attempt, List<Answer> answers) {

    public record Answer(UUID snapshotId, int sectionIndex, int position, QuestionType type, QuestionContent content,
                         AnswerKey answerKey, BigDecimal marks, AnswerResponse response, Instant savedAt,
                         BigDecimal marksAwarded, boolean autoGraded, UUID gradedBy, String feedback) {
    }
}
