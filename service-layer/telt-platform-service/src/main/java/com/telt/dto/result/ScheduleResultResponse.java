package com.telt.dto.result;

import com.telt.constants.ResultRelease;
import com.telt.constants.ResultStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code status} is null until results are submitted; only {@code MANUAL} schedules go through approval.
 * {@code released} says whether students can currently see their results.
 */
public record ScheduleResultResponse(UUID scheduleId, ResultRelease resultRelease, ResultStatus status,
                                     boolean released, long attempts, long open, long pendingManual,
                                     UUID submittedBy, Instant submittedAt, UUID decidedBy, Instant decidedAt,
                                     String comment, UUID publishedBy, Instant publishedAt) {
}
