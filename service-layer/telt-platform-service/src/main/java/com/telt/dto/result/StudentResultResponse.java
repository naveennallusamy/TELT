package com.telt.dto.result;

import com.telt.constants.AttemptStatus;
import com.telt.constants.QuestionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A released result. Answer keys are not included. {@code rank}, {@code percentile} (midrank, 0–100) and
 * {@code cohortSize} compare the score with every other student's best graded attempt; they are set once the window
 * has closed and no attempt of the schedule is still in progress.
 */
public record StudentResultResponse(UUID attemptId, UUID scheduleId, UUID examId, String examTitle, int attemptNo,
                                    AttemptStatus status, Instant submittedAt, BigDecimal score, BigDecimal maxScore,
                                    BigDecimal passMarks, Boolean passed, List<Answer> answers, Integer rank,
                                    BigDecimal percentile, Integer cohortSize) {

    public record Answer(int sectionIndex, int position, QuestionType type, String text, BigDecimal marks,
                         BigDecimal marksAwarded, String feedback) {
    }

    public StudentResultResponse withStanding(int rank, BigDecimal percentile, int cohortSize) {
        return new StudentResultResponse(attemptId, scheduleId, examId, examTitle, attemptNo, status, submittedAt, score,
                maxScore, passMarks, passed, answers, rank, percentile, cohortSize);
    }
}
