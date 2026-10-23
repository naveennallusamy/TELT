package com.telt.service;

import com.telt.dto.tenant.CreateTenantRequest;
import com.telt.dto.tenant.TenantResponse;
import com.telt.entity.tenant.Tenant;

import java.util.List;
import java.util.UUID;

public interface TenantService {

    TenantResponse create(CreateTenantRequest request);

    List<TenantResponse> findAll();

    TenantResponse findById(UUID tenantId);

    void delete(UUID tenantId);

    /**
     * Tenant an admin is acting on: super admins name it explicitly; tenant admins always get their own tenant
     * and are rejected if they name a different one.
     */
    Tenant resolveTargetTenant(String requestedTenantName, boolean required);
}
