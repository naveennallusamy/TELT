package com.telt.dto.report;

import com.telt.constants.QuestionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Report for one schedule. Statistics use each student's best closed, fully graded attempt. Percentages and rates are
 * 0–100. Answer keys are not included.
 */
public record ScheduleReport(UUID scheduleId, UUID examId, String examTitle, String scheduleTitle, Instant startsAt,
                             Instant endsAt, BigDecimal maxScore, BigDecimal passMarks, Counts counts, Stats stats,
                             List<Band> distribution, List<StudentRow> students, List<QuestionStat> questions) {

    /**
     * {@code assigned}, {@code attempted}, {@code graded}, {@code pendingGrading} and {@code absent} count students;
     * {@code inProgress}, {@code submitted} and {@code expired} count attempts.
     */
    public record Counts(int assigned, int attempted, int graded, int pendingGrading, int absent, int inProgress,
                         int submitted, int expired) {
    }

    public record Stats(int students, BigDecimal mean, BigDecimal median, BigDecimal min, BigDecimal max,
                        BigDecimal standardDeviation, BigDecimal passRate) {
    }

    /**
     * Students whose best score is at least {@code fromPercent} and below {@code toPercent} of the maximum (the last
     * band includes 100; scores below zero fall into the first).
     */
    public record Band(int fromPercent, int toPercent, int students) {
    }

    /**
     * {@code status}: {@code ABSENT}, {@code IN_PROGRESS}, {@code PENDING_GRADING} or {@code GRADED}.
     */
    public record StudentRow(UUID userId, String email, UUID departmentId, String status, int attempts, UUID attemptId,
                             BigDecimal score, BigDecimal percentage, Boolean passed, Integer rank,
                             BigDecimal percentile) {
    }

    /**
     * {@code discrimination} is the full-marks rate of the top third of students minus that of the bottom third
     * (needs at least three graded students). {@code optionCounts} counts how often each option was selected.
     */
    public record QuestionStat(UUID snapshotId, int sectionIndex, int position, QuestionType type, String text,
                               BigDecimal marks, int responses, BigDecimal averageMarks, BigDecimal fullMarksRate,
                               BigDecimal discrimination, Map<String, Long> optionCounts) {
    }
}
