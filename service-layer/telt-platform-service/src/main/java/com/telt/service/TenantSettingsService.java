package com.telt.service;

import com.telt.dto.tenant.TenantSettingsResponse;
import com.telt.dto.tenant.UpdateTenantSettingsRequest;
import com.telt.entity.tenant.Tenant;
import com.telt.error.ApiException;
import com.telt.repository.TenantRepository;
import com.telt.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantSettingsService {

    private final TenantRepository tenantRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public TenantSettingsResponse get() {
        return TenantSettingsResponse.from(currentTenant());
    }

    /**
     * The default access mode applies to sub-admins added afterwards; existing sub-admins keep theirs.
     */
    @Transactional
    public TenantSettingsResponse update(UpdateTenantSettingsRequest request) {
        Tenant tenant = currentTenant();
        tenant.setSubAdminDefaultAccessMode(request.subAdminDefaultAccessMode());
        tenant.setSeparationOfDuties(request.separationOfDuties());
        auditService.record(tenant.getTenantId(), "TENANT_SETTINGS_UPDATED", "TENANT", tenant.getTenantId(),
                Map.of("subAdminDefaultAccessMode", request.subAdminDefaultAccessMode(),
                        "separationOfDuties", request.separationOfDuties()));
        return TenantSettingsResponse.from(tenant);
    }

    private Tenant currentTenant() {
        return tenantRepository.findById(TenantContext.requireCurrentTenant())
                .orElseThrow(() -> ApiException.notFound("Tenant"));
    }
}
