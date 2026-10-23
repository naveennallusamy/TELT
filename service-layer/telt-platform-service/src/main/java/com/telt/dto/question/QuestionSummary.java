package com.telt.dto.question;

import com.telt.constants.Difficulty;
import com.telt.constants.QuestionStatus;
import com.telt.constants.QuestionType;
import com.telt.constants.QuestionVersionStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Search hit describing the question's latest version.
 */
public record QuestionSummary(UUID questionId, UUID bankId, UUID categoryId, QuestionType type, QuestionStatus status,
                              int latestVersion, QuestionVersionStatus latestVersionStatus, Integer approvedVersion,
                              String text, Difficulty difficulty, BigDecimal defaultMarks) {
}
