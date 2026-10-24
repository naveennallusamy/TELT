package com.telt.dto.result;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccommodationRequest(@NotNull @Min(1) @Max(600) Integer extraMinutes, @Size(max = 500) String reason) {
}
