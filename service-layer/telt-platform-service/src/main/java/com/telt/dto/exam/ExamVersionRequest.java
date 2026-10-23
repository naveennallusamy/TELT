package com.telt.dto.exam;

import com.telt.entity.exam.ExamSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ExamVersionRequest(@NotNull @Valid ExamSettings settings,
                                 @NotEmpty @Size(max = 50) List<@Valid @NotNull SectionRequest> sections) {
}
