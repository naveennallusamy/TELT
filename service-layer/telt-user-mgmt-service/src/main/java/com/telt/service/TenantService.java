package com.telt.service;

import com.telt.entity.Tenant;

public interface TenantService {
    Tenant findOrCreateTenant(String tenantName, String description);

    Tenant createTenant(String tenantName, String description);
}
