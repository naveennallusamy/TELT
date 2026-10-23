package com.telt.dto.question;

import com.telt.constants.RecordStatus;
import com.telt.entity.question.QuestionBank;

import java.util.UUID;

public record BankResponse(UUID bankId, UUID departmentId, String name, String description, RecordStatus status) {

    public static BankResponse from(QuestionBank b) {
        return new BankResponse(b.getBankId(), b.getDepartmentId(), b.getName(), b.getDescription(), b.getStatus());
    }
}
