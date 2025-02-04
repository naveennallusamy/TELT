package com.telt.service.impl;

import com.telt.entity.role.Role;
import com.telt.repository.RoleRepository;
import com.telt.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RoleServiceImpl implements RoleService {
    @Autowired
    private RoleRepository roleRepository;

    /**
     * Creates a new role with the given name.
     *
     * @param roleName the name of the role to create
     * @return the created Role object
     */
    @Override
    public Role createRole(String roleName) {
        return roleRepository.save(new Role(roleName));
    }

    /**
     * Finds a role by its name.
     *
     * @param name the name of the role to find
     * @return the Role object with the specified name
     * @throws RuntimeException if no role with the given name is found
     */
    @Override
    public Role findByName(String name) {
        return roleRepository.findByName(name).orElseThrow(() -> new RuntimeException("Role not found"));
    }
}
