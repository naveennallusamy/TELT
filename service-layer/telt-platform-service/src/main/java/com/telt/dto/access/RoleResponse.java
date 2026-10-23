package com.telt.dto.access;

import com.telt.constants.Permission;
import com.telt.entity.role.Role;

import java.util.List;

public record RoleResponse(String name, String description, boolean assignable, List<Permission> permissions) {

    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getName(), role.getDescription(), role.isAssignable(),
                role.getPermissions().stream().sorted().toList());
    }
}
