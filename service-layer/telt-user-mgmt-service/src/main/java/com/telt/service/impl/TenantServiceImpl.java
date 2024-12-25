package com.telt.service.impl;

import com.telt.entity.Tenant;
import com.telt.repository.TenantRepository;
import com.telt.service.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TenantServiceImpl implements TenantService {

    @Autowired
    private TenantRepository tenantRepository;

    /**
     * @param tenantName
     * @param description
     * @return
     */
    @Override
    public Tenant findOrCreateTenant(String tenantName, String description) {
        return tenantRepository.findByName(tenantName)
                .orElseGet(() -> createTenant(tenantName, description));
    }

    /**
     * @param tenantName
     * @param description
     * @return
     */
    @Override
    public Tenant createTenant(String tenantName, String description) {
        Tenant tenant = new Tenant();
        tenant.setName(tenantName);
        tenant.setDescription(description);
        return tenantRepository.save(tenant);
    }
}
