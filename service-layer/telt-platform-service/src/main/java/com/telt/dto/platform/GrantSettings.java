package com.telt.dto.platform;

import com.telt.constants.ReleasePolicy;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code releaseId} is required for {@code PINNED} and must be absent for {@code LATEST}.
 * {@code allowClone} requires {@code answerKeysVisible}, because a clone carries the answer key.
 */
public record GrantSettings(@NotNull ReleasePolicy releasePolicy, UUID releaseId, boolean allowClone,
                            boolean answerKeysVisible, Instant validFrom, Instant validUntil) {
}
