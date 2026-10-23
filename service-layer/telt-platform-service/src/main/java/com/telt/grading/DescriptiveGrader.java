package com.telt.grading;

import com.telt.constants.QuestionType;
import com.telt.entity.exam.AnswerResponse;
import com.telt.entity.exam.ExamQuestionSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

/**
 * Blank answers score zero; anything else waits for an evaluator.
 */
@Component
public class DescriptiveGrader implements QuestionGrader {

    @Override
    public Set<QuestionType> types() {
        return Set.of(QuestionType.DESCRIPTIVE);
    }

    @Override
    public Optional<BigDecimal> grade(ExamQuestionSnapshot question, AnswerResponse response, boolean negativeMarking) {
        if (response == null || response.text() == null || response.text().isBlank()) {
            return Optional.of(BigDecimal.ZERO);
        }
        return Optional.empty();
    }
}
