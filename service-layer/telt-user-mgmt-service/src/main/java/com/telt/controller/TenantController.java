package com.telt.controller;

import com.telt.entity.Tenant;
import com.telt.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tenant")
public class TenantController {
    @Autowired
    private TenantService tenantService;

    @PostMapping("/create")
    public ResponseEntity<?> createTenant(@RequestBody @Valid Tenant tenant) {
        try {
            return ResponseEntity.ok(tenantService.createTenant(tenant));
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<Tenant>> getAllTenants() {
        return ResponseEntity.ok(tenantService.findAll());
    }

    @DeleteMapping("/{tenantId}")
    public ResponseEntity<String> deleteTenant(@PathVariable Long tenantId) {
        tenantService.deleteTenant(tenantId);
        return ResponseEntity.ok("Tenant deleted successfully.");
    }
}
