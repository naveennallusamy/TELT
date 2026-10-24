package com.telt.dto.result;

import com.telt.constants.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResultDecisionRequest(@NotNull ReviewDecision decision, @Size(max = 2000) String comment) {
}
