package com.telt.service.impl;

import com.telt.constants.PlatformTenant;
import com.telt.dto.tenant.CreateTenantRequest;
import com.telt.dto.tenant.TenantResponse;
import com.telt.entity.tenant.Tenant;
import com.telt.error.ApiException;
import com.telt.repository.TenantRepository;
import com.telt.service.TenantService;
import com.telt.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;

    @Override
    @Transactional
    public TenantResponse create(CreateTenantRequest request) {
        String name = request.tenantName().trim();
        if (tenantRepository.existsByTenantNameIgnoreCase(name)) {
            throw ApiException.conflict("Tenant name already exists");
        }
        Tenant tenant = new Tenant();
        tenant.setTenantName(name);
        tenant.setDescription(request.description());
        return TenantResponse.from(tenantRepository.save(tenant));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantResponse> findAll() {
        return tenantRepository.findAll().stream().filter(t -> !PlatformTenant.is(t.getTenantId())).map(TenantResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TenantResponse findById(UUID tenantId) {
        return TenantResponse.from(load(tenantId));
    }

    @Override
    @Transactional
    public void delete(UUID tenantId) {
        tenantRepository.delete(load(tenantId));
        tenantRepository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public Tenant resolveTargetTenant(String requestedTenantName, boolean required) {
        boolean named = requestedTenantName != null && !requestedTenantName.isBlank();
        if (TenantContext.isSuperAdmin()) {
            if (!named) {
                if (required) {
                    throw ApiException.badRequest("tenantName is required");
                }
                return null;
            }
            return tenantRepository.findByTenantName(requestedTenantName.trim())
                    .filter(t -> !PlatformTenant.is(t.getTenantId()))
                    .orElseThrow(() -> ApiException.notFound("Tenant"));
        }
        UUID ownTenantId = TenantContext.getCurrentTenant();
        if (ownTenantId == null) {
            throw ApiException.forbidden("No tenant associated with the current user");
        }
        Tenant own = load(ownTenantId);
        if (named && !own.getTenantName().equalsIgnoreCase(requestedTenantName.trim())) {
            throw ApiException.forbidden("Tenant admins can only manage their own tenant");
        }
        return own;
    }

    private Tenant load(UUID tenantId) {
        return tenantRepository.findById(tenantId).filter(t -> !PlatformTenant.is(t.getTenantId()))
                .orElseThrow(() -> ApiException.notFound("Tenant"));
    }
}
