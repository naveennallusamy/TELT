package com.telt.dto.report;

import com.telt.constants.GrantStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * How a platform exam performed in each tenant it was distributed to, using each student's best graded attempt per
 * schedule. Percentages and rates are 0–100.
 */
public record PlatformExamReport(UUID examId, String code, String title, int tenants, int students,
                                 BigDecimal averagePercentage, BigDecimal passRate, List<TenantRow> perTenant) {

    public record TenantRow(UUID tenantId, String tenantName, GrantStatus distributionStatus, UUID tenantExamId,
                            int schedules, int students, BigDecimal averagePercentage, BigDecimal passRate) {
    }
}
