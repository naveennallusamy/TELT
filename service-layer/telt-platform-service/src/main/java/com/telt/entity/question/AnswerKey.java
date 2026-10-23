package com.telt.entity.question;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Server-side only. Choice types use {@code correctOptionIds}; numerical uses {@code value} ± {@code tolerance};
 * descriptive may carry a marking {@code rubric}.
 */
public record AnswerKey(List<String> correctOptionIds, BigDecimal value, BigDecimal tolerance,
                        @Size(max = 10000) String rubric) {

    public List<String> correctOrEmpty() {
        return correctOptionIds == null ? List.of() : correctOptionIds;
    }
}
