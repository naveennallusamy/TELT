package com.telt.grading;

import com.telt.constants.QuestionType;
import com.telt.entity.exam.AnswerResponse;
import com.telt.entity.exam.ExamQuestionSnapshot;
import com.telt.entity.question.AnswerKey;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

@Component
public class NumericalGrader implements QuestionGrader {

    @Override
    public Set<QuestionType> types() {
        return Set.of(QuestionType.NUMERICAL);
    }

    @Override
    public Optional<BigDecimal> grade(ExamQuestionSnapshot question, AnswerResponse response, boolean negativeMarking) {
        if (response == null || response.value() == null) {
            return Optional.of(BigDecimal.ZERO);
        }
        AnswerKey key = question.getAnswerKey();
        BigDecimal tolerance = key.tolerance() == null ? BigDecimal.ZERO : key.tolerance();
        boolean correct = response.value().subtract(key.value()).abs().compareTo(tolerance) <= 0;
        return Optional.of(correct ? question.getMarks() : QuestionGrader.wrong(question, negativeMarking));
    }
}
