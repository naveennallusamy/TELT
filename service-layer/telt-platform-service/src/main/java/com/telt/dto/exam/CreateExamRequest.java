package com.telt.dto.exam;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateExamRequest(@NotBlank @Size(max = 50) String code, @NotBlank @Size(max = 300) String title,
                                @Size(max = 2000) String description, UUID departmentId,
                                @NotNull @Valid ExamVersionRequest version) {
}
