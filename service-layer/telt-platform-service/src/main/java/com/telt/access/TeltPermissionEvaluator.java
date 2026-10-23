package com.telt.access;

import com.telt.constants.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.UUID;

/**
 * Backs {@code hasPermission(...)} in {@code @PreAuthorize}:
 * <ul>
 *   <li>{@code hasPermission(null, 'X')} - X in any scope (services then filter by scope)</li>
 *   <li>{@code hasPermission('TENANT', 'X')} - X tenant-wide</li>
 *   <li>{@code hasPermission(#departmentId, 'DEPARTMENT', 'X')} - X for that department (or tenant-wide)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TeltPermissionEvaluator implements PermissionEvaluator {

    private final AuthorizationService authorizationService;

    @Override
    public boolean hasPermission(Authentication authentication, Object target, Object permission) {
        Permission p = Permission.valueOf(permission.toString());
        AuthorizationSnapshot snapshot = authorizationService.current();
        return "TENANT".equals(target) ? snapshot.hasTenantWide(p) : snapshot.has(p);
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType,
                                 Object permission) {
        Permission p = Permission.valueOf(permission.toString());
        AuthorizationSnapshot snapshot = authorizationService.current();
        if ("DEPARTMENT".equals(targetType)) {
            return targetId == null ? snapshot.hasTenantWide(p)
                    : snapshot.allowsDepartment(p, UUID.fromString(targetId.toString()));
        }
        return snapshot.hasTenantWide(p);
    }
}
