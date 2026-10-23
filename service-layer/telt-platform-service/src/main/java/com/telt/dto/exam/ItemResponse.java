package com.telt.dto.exam;

import com.telt.entity.exam.ExamItem;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemResponse(UUID itemId, int position, UUID questionId, int questionVersionNo, UUID grantId,
                           BigDecimal marks, BigDecimal negativeMarks) {

    public static ItemResponse from(ExamItem i) {
        return new ItemResponse(i.getItemId(), i.getPosition(), i.getQuestionId(), i.getQuestionVersionNo(),
                i.getGrantId(), i.getMarks(), i.getNegativeMarks());
    }
}
