package com.telt.service.impl;

import com.telt.entity.Tenant;
import com.telt.repository.TenantRepository;
import com.telt.service.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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
                .orElseGet(() -> createTenant(new Tenant() {{
                    setName(tenantName);
                    setDescription(description);
                }}));
    }

    /**
     * @param tenant
     * @return
     */
    @Override
    public Tenant createTenant(Tenant tenant) {
        return tenantRepository.save(tenant);
    }

    /**
     * @return
     */
    @Override
    public List<Tenant> findAll() {
        return tenantRepository.findAll();
    }

    /**
     * @param tenantId
     */
    @Override
    public void deleteTenant(Long tenantId) {
        tenantRepository.deleteById(tenantId);
    }
}
