package com.telt.dto.question;

import com.telt.constants.QuestionStatus;
import com.telt.constants.QuestionType;
import com.telt.entity.question.Question;

import java.util.List;
import java.util.UUID;

public record QuestionResponse(UUID questionId, UUID bankId, UUID categoryId, QuestionType type,
                               QuestionStatus status, int latestVersion, Integer approvedVersion,
                               UUID sourceQuestionId, Integer sourceVersionNo, List<QuestionVersionResponse> versions) {

    public static QuestionResponse from(Question q, List<QuestionVersionResponse> versions) {
        return new QuestionResponse(q.getQuestionId(), q.getBankId(), q.getCategoryId(), q.getType(), q.getStatus(),
                q.getLatestVersion(), q.getApprovedVersion(), q.getSourceQuestionId(), q.getSourceVersionNo(), versions);
    }
}
