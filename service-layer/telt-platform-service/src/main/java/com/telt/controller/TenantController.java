package com.telt.controller;

import com.telt.dto.tenant.CreateTenantRequest;
import com.telt.dto.tenant.TenantResponse;
import com.telt.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class TenantController {

    private final TenantService tenantService;

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return tenantService.create(request);
    }

    @GetMapping
    public List<TenantResponse> getAllTenants() {
        return tenantService.findAll();
    }

    @GetMapping("/{tenantId}")
    public TenantResponse getTenant(@PathVariable UUID tenantId) {
        return tenantService.findById(tenantId);
    }

    @DeleteMapping("/{tenantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTenant(@PathVariable UUID tenantId) {
        tenantService.delete(tenantId);
    }
}
