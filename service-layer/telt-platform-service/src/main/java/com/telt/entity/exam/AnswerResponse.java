package com.telt.entity.exam;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * A student's answer, stored as JSONB: option IDs for choice questions, {@code value} for numerical and {@code text}
 * for descriptive. Further fields (e.g. language and code for programming questions) can be added later.
 */
public record AnswerResponse(@Size(max = 26) List<@NotBlank @Size(max = 20) String> selectedOptionIds,
                             BigDecimal value,
                             @Size(max = 50000) String text) {
}
