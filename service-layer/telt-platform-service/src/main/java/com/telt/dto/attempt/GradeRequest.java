package com.telt.dto.attempt;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record GradeRequest(@NotNull @DecimalMin("0") BigDecimal marks, @Size(max = 4000) String feedback) {
}
