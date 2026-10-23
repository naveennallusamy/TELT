package com.telt.dto.attempt;

import com.telt.constants.AttemptStatus;
import com.telt.constants.QuestionType;
import com.telt.entity.exam.AnswerResponse;
import com.telt.entity.question.QuestionOption;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The student's view of an attempt. Questions are listed only for the current section, in the student's order and
 * with options in the student's order; answer keys are never included.
 */
public record AttemptResponse(UUID attemptId, UUID scheduleId, UUID examId, int attemptNo, AttemptStatus status,
                              Instant startedAt, Instant deadlineAt, Instant submittedAt, int currentSection,
                              Instant sectionDeadlineAt, String instructions, List<Section> sections) {

    public enum SectionState {
        DONE, CURRENT, UPCOMING
    }

    public record Section(int index, String title, Integer timeLimitMinutes, SectionState state, int questionCount,
                          List<Question> questions) {
    }

    public record Question(UUID snapshotId, int position, QuestionType type, String text, List<QuestionOption> options,
                           BigDecimal marks, BigDecimal negativeMarks, AnswerResponse response, Instant savedAt) {
    }
}
