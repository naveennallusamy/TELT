package com.telt.grading;

import com.telt.constants.QuestionType;
import com.telt.entity.exam.AnswerResponse;
import com.telt.entity.exam.ExamQuestionSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class GraderRegistry {

    private final Map<QuestionType, QuestionGrader> graders = new EnumMap<>(QuestionType.class);

    public GraderRegistry(List<QuestionGrader> graders) {
        for (QuestionGrader grader : graders) {
            for (QuestionType type : grader.types()) {
                if (this.graders.put(type, grader) != null) {
                    throw new IllegalStateException("Two graders for " + type);
                }
            }
        }
    }

    /**
     * Types without a grader are left for manual grading.
     */
    public Optional<BigDecimal> grade(ExamQuestionSnapshot question, AnswerResponse response, boolean negativeMarking) {
        QuestionGrader grader = graders.get(question.getQuestionType());
        return grader == null ? Optional.empty() : grader.grade(question, response, negativeMarking);
    }
}
