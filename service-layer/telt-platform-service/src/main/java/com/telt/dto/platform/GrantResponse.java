package com.telt.dto.platform;

import com.telt.constants.GrantStatus;
import com.telt.constants.ReleasePolicy;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code effectiveReleaseId}/{@code effectiveReleaseNo} are the release the tenant currently sees (null when none).
 */
public record GrantResponse(UUID grantId, UUID tenantId, UUID bankId, String bankName, ReleasePolicy releasePolicy,
                            UUID releaseId, UUID effectiveReleaseId, Integer effectiveReleaseNo, boolean allowClone,
                            boolean answerKeysVisible, Instant validFrom, Instant validUntil, GrantStatus status) {
}
