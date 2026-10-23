package com.telt.dto.question;

import com.telt.constants.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequest(@NotNull ReviewDecision decision, @Size(max = 2000) String comments) {
}
