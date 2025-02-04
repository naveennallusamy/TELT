package com.telt.controller;

import com.telt.entity.tenant.Tenant;
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

    /**
     * Creates a new tenant.
     * <p>
     * This endpoint is used to create a new tenant in the system. The tenant
     * information is passed as a request body in JSON format.
     *
     * @param tenant The tenant information to be created.
     * @return ResponseEntity containing the created tenant with HTTP status
     * CREATED, or an error message with HTTP status INTERNAL_SERVER_ERROR if
     * the tenant creation fails due to a database constraint violation.
     */
    @PostMapping("/create")
    public ResponseEntity<?> createTenant(@RequestBody @Valid Tenant tenant) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.createTenant(tenant));
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    /**
     * Retrieves a list of all tenants.
     *
     * @return ResponseEntity containing a list of Tenant objects with HTTP status OK.
     */
    @GetMapping
    public ResponseEntity<List<Tenant>> getAllTenants() {
        return ResponseEntity.ok(tenantService.findAll());
    }

    /**
     * Deletes a tenant with the specified tenant ID.
     * <p>
     * This endpoint is used to delete a tenant from the system based on the
     * provided tenant ID. If the deletion is successful, it returns a success
     * message with HTTP status OK.
     *
     * @param tenantId The ID of the tenant to be deleted.
     * @return ResponseEntity containing a success message with HTTP status OK.
     */
    @DeleteMapping("/{tenantId}")
    public ResponseEntity<String> deleteTenant(@PathVariable Long tenantId) {
        tenantService.deleteTenant(tenantId);
        return ResponseEntity.ok("Tenant deleted successfully.");
    }
}
