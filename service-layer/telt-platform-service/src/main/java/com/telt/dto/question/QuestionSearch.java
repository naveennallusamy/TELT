package com.telt.dto.question;

import com.telt.constants.Difficulty;
import com.telt.constants.QuestionType;
import com.telt.constants.QuestionVersionStatus;

import java.util.UUID;

public record QuestionSearch(UUID bankId, UUID categoryId, QuestionType type, QuestionVersionStatus status,
                             Difficulty difficulty, String tag, String q, int page, int size) {
}
