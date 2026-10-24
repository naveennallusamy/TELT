package com.telt.dto.result;

import com.telt.constants.AttemptStatus;
import com.telt.constants.QuestionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A released result. Answer keys are not included.
 */
public record StudentResultResponse(UUID attemptId, UUID scheduleId, UUID examId, String examTitle, int attemptNo,
                                    AttemptStatus status, Instant submittedAt, BigDecimal score, BigDecimal maxScore,
                                    BigDecimal passMarks, Boolean passed, List<Answer> answers) {

    public record Answer(int sectionIndex, int position, QuestionType type, String text, BigDecimal marks,
                         BigDecimal marksAwarded, String feedback) {
    }
}
