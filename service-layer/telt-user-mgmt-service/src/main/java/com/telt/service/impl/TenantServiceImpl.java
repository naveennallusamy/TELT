package com.telt.service.impl;

import com.telt.entity.tenant.Tenant;
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
     * Finds a tenant by its name, or creates a new tenant if one does not already exist.
     * <p>
     * This method is used to find a tenant by its name, or create a new tenant if one does not already exist.
     * If a tenant with the given name already exists, it is returned. Otherwise,
     * a new tenant is created with the given name and description and saved to the repository.
     * If a tenant with the same name already exists, a DataIntegrityViolationException
     * is thrown indicating that the tenant name must be unique.
     *
     * @param tenantName the name of the tenant to find or create
     * @param description the description of the new tenant to create
     * @return the Tenant object found or created
     * @throws DataIntegrityViolationException if the tenant name already exists
     */
    @Override
    public Tenant findOrCreateTenant(String tenantName, String description) {
        return tenantRepository.findByTenantName(tenantName).orElseGet(() -> createTenant(new Tenant() {{
            setTenantName(tenantName);
            setDescription(description);
        }}));
    }

    /**
     * Finds a tenant by its name.
     *
     * @param tenantName the name of the tenant to find
     * @return the Tenant object with the specified name
     * @throws RuntimeException if no tenant with the given name is found
     */
    @Override
    public Tenant findByTenantName(String tenantName) {
        return tenantRepository.findByTenantName(tenantName).orElseThrow(() -> new RuntimeException("Tenant Information not found"));
    }

    /**
     * Creates a new tenant in the system.
     * <p>
     * This method attempts to save the provided tenant object in the repository.
     * If a tenant with the same name already exists, a DataIntegrityViolationException
     * is thrown indicating that the tenant name must be unique.
     *
     * @param tenant The tenant object to be created.
     * @return The saved Tenant object if successful, null otherwise.
     * @throws DataIntegrityViolationException if the tenant name already exists.
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
     * Finds all tenants in the system.
     *
     * @return a list of all {@link Tenant} objects in the system
     */
    @Override
    public List<Tenant> findAll() {
        return tenantRepository.findAll();
    }

    /**
     * Deletes a tenant with the specified tenant ID.
     *
     * @param tenantId The ID of the tenant to be deleted.
     */
    @Override
    public void deleteTenant(Long tenantId) {
        tenantRepository.deleteById(tenantId);
    }
}
