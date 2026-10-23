package com.telt.dto.access;

import com.telt.access.AuthorizationSnapshot;
import com.telt.access.PermissionScope;
import com.telt.constants.AccessMode;
import com.telt.constants.MembershipType;
import com.telt.constants.Permission;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Effective permissions of the caller in the active tenant; used by the UI to show or hide features.
 */
public record AuthorizationResponse(UUID tenantId, boolean superAdmin, MembershipType membershipType,
                                    AccessMode accessMode, UUID departmentId, Map<Permission, PermissionScope> permissions) {

    public static AuthorizationResponse from(AuthorizationSnapshot s) {
        return new AuthorizationResponse(s.tenantId(), s.superAdmin(), s.membershipType(), s.accessMode(),
                s.departmentId(), new TreeMap<>(s.grants()));
    }
}
