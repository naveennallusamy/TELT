package com.telt.service;

import com.telt.entity.Tenant;

import java.util.List;

public interface TenantService {
    Tenant findOrCreateTenant(String tenantName, String description);

    Tenant createTenant(Tenant tenant);

    List<Tenant> findAll();

    void deleteTenant(Long tenantId);
}
