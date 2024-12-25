package com.telt.service;

import com.telt.entity.Role;

public interface RoleService {
    Role createRole(String roleName);

    Role findByName(String name);
}
