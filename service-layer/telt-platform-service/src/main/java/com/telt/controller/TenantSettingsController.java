package com.telt.controller;

import com.telt.dto.tenant.TenantSettingsResponse;
import com.telt.dto.tenant.UpdateTenantSettingsRequest;
import com.telt.service.TenantSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tenant-settings")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('TENANT', 'TENANT_SETTINGS_MANAGE')")
public class TenantSettingsController {

    private final TenantSettingsService tenantSettingsService;

    @GetMapping
    public TenantSettingsResponse get() {
        return tenantSettingsService.get();
    }

    @PutMapping
    public TenantSettingsResponse update(@Valid @RequestBody UpdateTenantSettingsRequest request) {
        return tenantSettingsService.update(request);
    }
}
