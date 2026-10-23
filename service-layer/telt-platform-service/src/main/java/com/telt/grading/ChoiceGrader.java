package com.telt.grading;

import com.telt.constants.QuestionType;
import com.telt.entity.exam.AnswerResponse;
import com.telt.entity.exam.ExamQuestionSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Full marks only for exactly the correct options; unanswered scores zero.
 */
@Component
public class ChoiceGrader implements QuestionGrader {

    @Override
    public Set<QuestionType> types() {
        return Set.of(QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_SELECT);
    }

    @Override
    public Optional<BigDecimal> grade(ExamQuestionSnapshot question, AnswerResponse response, boolean negativeMarking) {
        if (response == null || response.selectedOptionIds() == null || response.selectedOptionIds().isEmpty()) {
            return Optional.of(BigDecimal.ZERO);
        }
        boolean correct = new HashSet<>(response.selectedOptionIds())
                .equals(new HashSet<>(question.getAnswerKey().correctOrEmpty()));
        return Optional.of(correct ? question.getMarks() : QuestionGrader.wrong(question, negativeMarking));
    }
}
