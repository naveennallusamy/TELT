package com.telt.grading;

import com.telt.constants.QuestionType;
import com.telt.entity.exam.AnswerResponse;
import com.telt.entity.exam.ExamQuestionSnapshot;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

/**
 * Marks one answer of the question types it supports. New types (e.g. programming questions run in a sandbox) plug
 * in as further beans.
 */
public interface QuestionGrader {

    Set<QuestionType> types();

    /**
     * Marks for the answer, or empty when a person has to grade it. {@code response} is null when unanswered.
     */
    Optional<BigDecimal> grade(ExamQuestionSnapshot question, AnswerResponse response, boolean negativeMarking);

    static BigDecimal wrong(ExamQuestionSnapshot question, boolean negativeMarking) {
        return negativeMarking ? question.getNegativeMarks().negate() : BigDecimal.ZERO;
    }
}
