package com.telt.access;

import com.telt.constants.AccessMode;
import com.telt.constants.MembershipType;
import com.telt.constants.Permission;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Effective permissions of one user in one tenant, computed from membership type and role assignments.
 * Super admins act across tenants and pass every tenant permission check.
 */
public record AuthorizationSnapshot(UUID tenantId, UUID userId, boolean superAdmin, MembershipType membershipType,
                                    AccessMode accessMode, UUID departmentId, Map<Permission, PermissionScope> grants) {

    public static AuthorizationSnapshot superAdmin(UUID userId) {
        return new AuthorizationSnapshot(null, userId, true, null, null, null, Map.of());
    }

    public static AuthorizationSnapshot none(UUID tenantId, UUID userId) {
        return new AuthorizationSnapshot(tenantId, userId, false, null, null, null, Map.of());
    }

    public boolean has(Permission permission) {
        return superAdmin || grants.containsKey(permission);
    }

    public boolean hasTenantWide(Permission permission) {
        return superAdmin || scope(permission).map(PermissionScope::tenantWide).orElse(false);
    }

    public boolean allowsDepartment(Permission permission, UUID departmentId) {
        return superAdmin || scope(permission).map(s -> s.coversDepartment(departmentId)).orElse(false);
    }

    public boolean allowsResource(Permission permission, UUID resourceId, UUID departmentId) {
        return superAdmin || scope(permission).map(s -> s.coversResource(resourceId, departmentId)).orElse(false);
    }

    public Optional<PermissionScope> scope(Permission permission) {
        return superAdmin ? Optional.of(PermissionScope.tenant()) : Optional.ofNullable(grants.get(permission));
    }
}
