package com.telt.dto.question;

import com.telt.constants.Difficulty;
import com.telt.entity.question.AnswerKey;
import com.telt.entity.question.QuestionContent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record QuestionVersionRequest(@NotNull @Valid QuestionContent content, @Valid AnswerKey answerKey,
                                     @Size(max = 10000) String explanation,
                                     @DecimalMin("0") BigDecimal defaultMarks, @DecimalMin("0") BigDecimal negativeMarks,
                                     Difficulty difficulty, @Size(max = 20) List<@NotBlank @Size(max = 50) String> tags) {
}
