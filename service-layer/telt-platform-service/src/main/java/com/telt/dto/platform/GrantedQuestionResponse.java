package com.telt.dto.platform;

import com.telt.constants.QuestionType;
import com.telt.dto.question.QuestionVersionResponse;

import java.util.UUID;

public record GrantedQuestionResponse(UUID questionId, UUID categoryId, QuestionType type,
                                      QuestionVersionResponse version) {
}
