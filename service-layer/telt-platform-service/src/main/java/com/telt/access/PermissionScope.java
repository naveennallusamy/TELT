package com.telt.access;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Where a permission applies: the whole tenant, a set of departments (already expanded to descendants) and/or
 * individual resources.
 */
public record PermissionScope(boolean tenantWide, Set<UUID> departmentIds, Set<UUID> resourceIds) {

    public static PermissionScope tenant() {
        return new PermissionScope(true, Set.of(), Set.of());
    }

    public boolean coversDepartment(UUID departmentId) {
        return tenantWide || (departmentId != null && departmentIds.contains(departmentId));
    }

    public boolean coversResource(UUID resourceId, UUID departmentId) {
        return coversDepartment(departmentId) || (resourceId != null && resourceIds.contains(resourceId));
    }

    PermissionScope merge(PermissionScope other) {
        Set<UUID> departments = new HashSet<>(departmentIds);
        departments.addAll(other.departmentIds);
        Set<UUID> resources = new HashSet<>(resourceIds);
        resources.addAll(other.resourceIds);
        return new PermissionScope(tenantWide || other.tenantWide, Set.copyOf(departments), Set.copyOf(resources));
    }
}
