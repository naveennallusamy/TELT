package com.telt.dto.exam;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * {@code pickCount}: when set, each attempt draws that many of the listed items at random (pick N of M).
 * {@code timeLimitMinutes}: optional section timer; the total of all section timers may not exceed the exam duration.
 */
public record SectionRequest(@NotBlank @Size(max = 300) String title, @Min(1) Integer pickCount,
                             @Min(1) @Max(1440) Integer timeLimitMinutes,
                             @NotEmpty @Size(max = 500) List<@Valid @NotNull ItemRequest> items) {
}
