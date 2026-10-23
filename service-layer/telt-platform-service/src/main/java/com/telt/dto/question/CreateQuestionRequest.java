package com.telt.dto.question;

import com.telt.constants.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateQuestionRequest(@NotNull QuestionType type, UUID categoryId,
                                    @NotNull @Valid QuestionVersionRequest version) {
}
