package com.telt.entity.question;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuestionOption(@NotBlank @Size(max = 20) String id, @NotBlank @Size(max = 4000) String text) {
}
