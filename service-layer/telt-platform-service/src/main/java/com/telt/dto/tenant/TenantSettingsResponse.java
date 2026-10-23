package com.telt.dto.tenant;

import com.telt.constants.AccessMode;
import com.telt.entity.tenant.Tenant;

import java.util.UUID;

public record TenantSettingsResponse(UUID tenantId, String tenantName, AccessMode subAdminDefaultAccessMode,
                                     boolean separationOfDuties) {

    public static TenantSettingsResponse from(Tenant t) {
        return new TenantSettingsResponse(t.getTenantId(), t.getTenantName(), t.getSubAdminDefaultAccessMode(),
                t.isSeparationOfDuties());
    }
}
