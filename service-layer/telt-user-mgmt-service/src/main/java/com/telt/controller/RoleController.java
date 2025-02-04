package com.telt.controller;

import com.telt.entity.role.Role;
import com.telt.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/roles")
public class RoleController {
    @Autowired
    private RoleService roleService;

    /**
     * Creates a new role in the system.
     * <p>
     * This endpoint is used to create a new role in the system. The role name
     * is passed as a request parameter.
     *
     * @param roleName The name of the role to be created.
     * @return ResponseEntity containing the created role with HTTP status
     * CREATED.
     */
    @PostMapping("/create")
    public ResponseEntity<Role> createRole(@RequestParam String roleName) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.createRole(roleName));
    }
}
