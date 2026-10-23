package com.telt.dto.schedule;

import com.telt.entity.exam.OverridePolicy;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Sends the platform exam's published version to a tenant. Re-sending after a new version is published updates the
 * tenant's copy; existing schedules keep the version they were created with. {@code answerKeysVisible} (default off)
 * lets tenant staff see answer keys in the copy; it applies to the versions sent with this request.
 */
public record DistributeExamRequest(@NotNull UUID tenantId, OverridePolicy overridePolicy, Boolean answerKeysVisible) {
}
