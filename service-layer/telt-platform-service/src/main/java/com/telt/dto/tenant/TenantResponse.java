package com.telt.dto.tenant;

import com.telt.entity.tenant.Tenant;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(UUID tenantId, String tenantName, String description, Instant createdOn) {

    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(tenant.getTenantId(), tenant.getTenantName(), tenant.getDescription(),
                tenant.getCreatedOn());
    }
}
