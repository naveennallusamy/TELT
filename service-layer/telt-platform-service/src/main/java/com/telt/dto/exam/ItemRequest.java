package com.telt.dto.exam;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Own question: {@code grantId} empty, uses its approved version. Platform question: {@code grantId} set, uses the
 * version in the grant's current release. {@code marks}/{@code negativeMarks} override the question's defaults.
 */
public record ItemRequest(@NotNull UUID questionId, UUID grantId, @DecimalMin("0") BigDecimal marks,
                          @DecimalMin("0") BigDecimal negativeMarks) {
}
