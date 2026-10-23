package com.telt.controller;

import com.telt.dto.organization.OrganizationResponse;
import com.telt.dto.organization.RegisterOrganizationRequest;
import com.telt.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/organization")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'TENANT_ADMIN')")
public class OrganizationController {

    private final OrganizationService organizationService;

    @Operation(summary = "Register organization details for a tenant")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse register(@Valid @RequestBody RegisterOrganizationRequest request) {
        return organizationService.register(request);
    }
}
