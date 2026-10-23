package com.telt.dto.schedule;

import com.telt.entity.exam.ScheduleOverrides;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code examId} is required when creating and ignored when updating. The schedule runs the exam's published version.
 */
public record ScheduleRequest(UUID examId,
                              @NotBlank @Size(max = 300) String title,
                              @NotNull Instant startsAt,
                              @NotNull Instant endsAt,
                              @NotBlank @Size(max = 60) String timeZone,
                              @Min(0) @Max(1440) Integer lateEntryMinutes,
                              @Valid ScheduleOverrides overrides) {
}
