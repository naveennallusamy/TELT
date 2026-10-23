package com.telt.dto.question;

import com.telt.constants.Difficulty;
import com.telt.constants.QuestionVersionStatus;
import com.telt.entity.question.AnswerKey;
import com.telt.entity.question.QuestionContent;
import com.telt.entity.question.QuestionVersion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code answerKey} and {@code explanation} are null unless the caller may see answer keys.
 */
public record QuestionVersionResponse(int versionNo, QuestionVersionStatus status, QuestionContent content,
                                      AnswerKey answerKey, String explanation, BigDecimal defaultMarks,
                                      BigDecimal negativeMarks, Difficulty difficulty, List<String> tags,
                                      UUID authoredBy, Instant createdOn, Instant submittedAt, UUID reviewedBy,
                                      Instant reviewedAt) {

    public static QuestionVersionResponse from(QuestionVersion v, boolean withAnswerKey) {
        return new QuestionVersionResponse(v.getVersionNo(), v.getStatus(), v.getContent(),
                withAnswerKey ? v.getAnswerKey() : null, withAnswerKey ? v.getExplanation() : null,
                v.getDefaultMarks(), v.getNegativeMarks(), v.getDifficulty(), List.copyOf(v.getTags()),
                v.getAuthoredBy(), v.getCreatedOn(), v.getSubmittedAt(), v.getReviewedBy(), v.getReviewedAt());
    }
}
