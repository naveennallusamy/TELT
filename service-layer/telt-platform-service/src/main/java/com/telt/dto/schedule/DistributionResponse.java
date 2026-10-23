package com.telt.dto.schedule;

import com.telt.constants.GrantStatus;
import com.telt.entity.exam.ExamDistribution;
import com.telt.entity.exam.OverridePolicy;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code activeSchedules} counts the tenant's non-cancelled schedules of its copy.
 */
public record DistributionResponse(UUID distributionId, UUID tenantId, UUID sourceExamId, int sourceVersionNo,
                                   UUID examId, OverridePolicy overridePolicy, boolean answerKeysVisible,
                                   GrantStatus status,
                                   Instant distributedAt, Instant revokedAt, long activeSchedules) {

    public static DistributionResponse from(ExamDistribution d, long activeSchedules) {
        return new DistributionResponse(d.getDistributionId(), d.getTenantId(), d.getSourceExamId(),
                d.getSourceVersionNo(), d.getExamId(), d.getOverridePolicy(), d.isAnswerKeysVisible(), d.getStatus(), d.getDistributedAt(),
                d.getRevokedAt(), activeSchedules);
    }
}
