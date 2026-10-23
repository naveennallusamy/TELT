package com.telt.access;

import com.telt.constants.Permission;
import com.telt.constants.PlatformTenant;
import com.telt.entity.question.QuestionBank;
import com.telt.error.ApiException;
import com.telt.error.ErrorCode;
import com.telt.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Question-bank permissions are evaluated against the bank's department: department-scoped staff reach banks in
 * their department tree, banks without a department need tenant-wide access.
 */
@Component
@RequiredArgsConstructor
public class QuestionAccess {

    private final AuthorizationService authorizationService;

    /**
     * Tenant whose banks the caller authors: their active tenant, or the platform tenant for a super admin
     * without one.
     */
    public UUID ownerTenant() {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null && TenantContext.isSuperAdmin()) {
            return PlatformTenant.ID;
        }
        return TenantContext.requireCurrentTenant();
    }

    public boolean can(Permission permission, QuestionBank bank) {
        AuthorizationSnapshot snapshot = authorizationService.current();
        return bank.getDepartmentId() == null ? snapshot.hasTenantWide(permission)
                : snapshot.allowsDepartment(permission, bank.getDepartmentId());
    }

    public void require(Permission permission, QuestionBank bank) {
        if (!can(permission, bank)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Access denied");
        }
    }

    public boolean canSeeAnswerKeys(QuestionBank bank) {
        return can(Permission.ANSWER_KEY_VIEW, bank) || can(Permission.QUESTION_WRITE, bank);
    }

    /**
     * Banks the caller may view, from {@code banks}; empty when they hold no view permission.
     */
    public List<QuestionBank> visible(Collection<QuestionBank> banks) {
        Optional<PermissionScope> scope = authorizationService.current().scope(Permission.QUESTION_VIEW);
        if (scope.isEmpty()) {
            return List.of();
        }
        if (scope.get().tenantWide()) {
            return List.copyOf(banks);
        }
        return banks.stream().filter(b -> b.getDepartmentId() != null
                && scope.get().departmentIds().contains(b.getDepartmentId())).toList();
    }
}
