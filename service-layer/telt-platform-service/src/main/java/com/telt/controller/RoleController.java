package com.telt.controller;

import com.telt.constants.Permission;
import com.telt.dto.access.RoleResponse;
import com.telt.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Read-only catalog of seeded roles and permissions. Custom roles are not supported.
 */
@RestController
@RequiredArgsConstructor
public class RoleController {

    private final RoleRepository roleRepository;

    @GetMapping("/roles")
    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream().map(RoleResponse::from)
                .sorted(Comparator.comparing(RoleResponse::name)).toList();
    }

    @GetMapping("/permissions")
    public List<Permission> listPermissions() {
        return Arrays.stream(Permission.values()).toList();
    }
}
