package com.telt.service.impl;

import com.telt.entity.Tenant;
import com.telt.repository.TenantRepository;
import com.telt.service.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
        return tenantRepository.findByTenantName(tenantName).orElseGet(() -> createTenant(new Tenant() {{
            setTenantName(tenantName);
            setDescription(description);
        }}));
    }

    /**
     * @param tenantName
     * @return
     */
    @Override
    public Tenant findByTenantName(String tenantName) {
        return tenantRepository.findByTenantName(tenantName).orElseThrow(() -> new RuntimeException("Tenant Information not found"));
    }

    /**
     * @param tenant
     * @return
     */
    @Override
    public Tenant createTenant(Tenant tenant) {
        try {
            return tenantRepository.save(tenant);
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage().contains("tenant_name")) {
                System.out.println("Unique name constraint violated");
                throw new DataIntegrityViolationException("Tenant name already exists");
            }
        }
        return null;
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
