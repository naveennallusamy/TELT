package com.telt.dto.question;

import com.telt.entity.question.QuestionCategory;

import java.util.UUID;

public record CategoryResponse(UUID categoryId, UUID parentCategoryId, String name) {

    public static CategoryResponse from(QuestionCategory c) {
        return new CategoryResponse(c.getCategoryId(), c.getParentCategoryId(), c.getName());
    }
}
