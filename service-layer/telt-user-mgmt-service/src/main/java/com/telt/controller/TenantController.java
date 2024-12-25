package com.telt.controller;

import com.telt.entity.Tenant;
import com.telt.service.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant")
public class TenantController {
    @Autowired
    private TenantService tenantService;

    @PostMapping("/create")
    public ResponseEntity<Tenant> createTenant(@RequestParam String tenantName,
                                               @RequestParam(required = false) String description) {
        return ResponseEntity.ok(tenantService.createTenant(tenantName, description));
    }
}
