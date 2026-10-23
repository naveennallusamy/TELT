package com.telt.entity.question;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Student-visible part of a question version, stored as JSONB.
 */
public record QuestionContent(@NotBlank @Size(max = 20000) String text,
                              @Valid @Size(max = 26) List<QuestionOption> options) {

    public List<QuestionOption> optionsOrEmpty() {
        return options == null ? List.of() : options;
    }
}
