package com.telt.service;

import com.telt.entity.tenant.Tenant;

import java.util.List;

public interface TenantService {
    Tenant findOrCreateTenant(String tenantName, String description);

    Tenant findByTenantName(String tenantName);

    Tenant createTenant(Tenant tenant);

    List<Tenant> findAll();

    void deleteTenant(Long tenantId);
}
