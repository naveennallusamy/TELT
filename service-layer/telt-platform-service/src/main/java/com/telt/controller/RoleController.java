package com.telt.controller;

import com.telt.entity.role.Role;
import com.telt.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lists the seeded system roles. Custom roles are not supported.
 */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'TENANT_ADMIN')")
public class RoleController {

    private final RoleRepository roleRepository;

    @GetMapping
    public List<String> listRoles() {
        return roleRepository.findAll().stream().map(Role::getName).sorted().toList();
    }
}
