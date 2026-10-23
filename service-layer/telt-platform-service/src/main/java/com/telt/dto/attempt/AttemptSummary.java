package com.telt.dto.attempt;

import com.telt.constants.AttemptStatus;
import com.telt.constants.GradingStatus;
import com.telt.entity.exam.ExamAttempt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Staff view of an attempt in a schedule. Students get {@link AttemptResponse} without scores.
 */
public record AttemptSummary(UUID attemptId, UUID scheduleId, UUID userId, int attemptNo, AttemptStatus status,
                             Instant startedAt, Instant deadlineAt, Instant submittedAt, int currentSection,
                             long answered, BigDecimal score, BigDecimal maxScore, GradingStatus gradingStatus) {

    public static AttemptSummary from(ExamAttempt a, long answered) {
        return new AttemptSummary(a.getAttemptId(), a.getScheduleId(), a.getUserId(), a.getAttemptNo(), a.getStatus(),
                a.getStartedAt(), a.getDeadlineAt(), a.getSubmittedAt(), a.getCurrentSection(), answered, a.getScore(),
                a.getMaxScore(), a.getGradingStatus());
    }
}
