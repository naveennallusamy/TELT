package com.telt.entity.exam;

import com.telt.constants.ResultRelease;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ExamSettings(
        @NotNull @Min(1) @Max(1440) Integer durationMinutes,
        @Min(1) @Max(100) Integer maxAttempts,
        Boolean shuffleQuestions,
        Boolean shuffleOptions,
        Boolean negativeMarking,
        @DecimalMin("0") BigDecimal passMarks,
        @Size(max = 10000) String instructions,
        ResultRelease resultRelease) {

    public ExamSettings normalized() {
        return new ExamSettings(durationMinutes, maxAttempts == null ? 1 : maxAttempts,
                Boolean.TRUE.equals(shuffleQuestions), Boolean.TRUE.equals(shuffleOptions),
                Boolean.TRUE.equals(negativeMarking), passMarks, instructions,
                resultRelease == null ? ResultRelease.AFTER_WINDOW : resultRelease);
    }
}
