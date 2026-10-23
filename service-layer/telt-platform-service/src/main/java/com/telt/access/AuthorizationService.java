package com.telt.access;

import com.telt.persistence.RlsContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.telt.constants.AccessMode;
import com.telt.constants.MembershipType;
import com.telt.constants.Permission;
import com.telt.constants.ScopeType;
import com.telt.entity.role.Role;
import com.telt.entity.role.RoleAssignment;
import com.telt.entity.tenant.Department;
import com.telt.entity.tenant.TenantMembership;
import com.telt.error.ApiException;
import com.telt.error.ErrorCode;
import com.telt.repository.DepartmentRepository;
import com.telt.repository.RoleAssignmentRepository;
import com.telt.repository.RoleRepository;
import com.telt.repository.TenantMembershipRepository;
import com.telt.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Builds and caches {@link AuthorizationSnapshot}s (Caffeine, per pod). Writers evict locally via {@link #evictUser} /
 * {@link #evictTenant}; other instances are told through PostgreSQL notifications
 * ({@link AuthorizationInvalidationListener}), and the TTL bounds staleness if a notification is lost.
 */
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    public static final String TENANT_ADMIN_ROLE = "TENANT_ADMIN";
    public static final String STUDENT_ROLE = "STUDENT";

    private final TenantMembershipRepository membershipRepository;
    private final RoleAssignmentRepository assignmentRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final PlatformTransactionManager transactionManager;

    private final Cache<Key, AuthorizationSnapshot> snapshots = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();
    private volatile Map<String, Set<Permission>> rolePermissions;

    private record Key(UUID tenantId, UUID userId) {
    }

    /**
     * Snapshot for the authenticated caller.
     */
    public AuthorizationSnapshot current() {
        UUID userId = TenantContext.requireCurrentUserId();
        if (TenantContext.isSuperAdmin()) {
            return AuthorizationSnapshot.superAdmin(userId);
        }
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            return AuthorizationSnapshot.none(null, userId);
        }
        return snapshot(tenantId, userId);
    }

    public AuthorizationSnapshot snapshot(UUID tenantId, UUID userId) {
        return snapshots.get(new Key(tenantId, userId), key -> load(key.tenantId(), key.userId()));
    }

    public void require(Supplier<Boolean> check) {
        if (!check.get()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Access denied");
        }
    }

    public void evictUser(UUID tenantId, UUID userId) {
        snapshots.invalidate(new Key(tenantId, userId));
    }

    public void evictAll() {
        snapshots.invalidateAll();
    }

    public void evictTenant(UUID tenantId) {
        snapshots.asMap().keySet().removeIf(key -> key.tenantId().equals(tenantId));
    }

    public Set<Permission> permissionsOf(String roleName) {
        return rolePermissions().getOrDefault(roleName, Set.of());
    }

    /**
     * Reads are filtered by the requested tenant and user explicitly, so they run with RLS bypassed.
     */
    private AuthorizationSnapshot load(UUID tenantId, UUID userId) {
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        return RlsContext.asSystem(() -> readOnly.execute(status -> build(tenantId, userId)));
    }

    private AuthorizationSnapshot build(UUID tenantId, UUID userId) {
        Optional<TenantMembership> membership = membershipRepository.findByTenantIdAndUser_Id(tenantId, userId)
                .filter(TenantMembership::isActive);
        if (membership.isEmpty()) {
            return AuthorizationSnapshot.none(tenantId, userId);
        }
        TenantMembership m = membership.get();
        Map<Permission, PermissionScope> grants = new EnumMap<>(Permission.class);
        switch (m.getMembershipType()) {
            case TENANT_ADMIN -> permissionsOf(TENANT_ADMIN_ROLE).forEach(p -> grants.put(p, PermissionScope.tenant()));
            case STUDENT -> permissionsOf(STUDENT_ROLE).forEach(p -> grants.put(p, PermissionScope.tenant()));
            case SUB_ADMIN -> addAssignments(m, grants);
        }
        return new AuthorizationSnapshot(tenantId, userId, false, m.getMembershipType(), m.getAccessMode(),
                m.getDepartmentId(), Map.copyOf(grants));
    }

    private void addAssignments(TenantMembership membership, Map<Permission, PermissionScope> grants) {
        List<RoleAssignment> assignments = assignmentRepository.findByTenantIdAndUserId(
                membership.getTenantId(), membership.getUser().getId());
        if (assignments.isEmpty()) {
            return;
        }
        Map<UUID, Set<UUID>> descendants = descendants(membership.getTenantId());
        boolean tenantWideAllowed = membership.getAccessMode() == AccessMode.TENANT_WIDE;
        for (RoleAssignment assignment : assignments) {
            PermissionScope scope = switch (assignment.getScopeType()) {
                case TENANT -> tenantWideAllowed ? PermissionScope.tenant() : null;
                case DEPARTMENT -> new PermissionScope(false,
                        descendants.getOrDefault(assignment.getDepartmentId(), Set.of()), Set.of());
                case RESOURCE -> new PermissionScope(false, Set.of(), Set.of(assignment.getResourceId()));
            };
            if (scope == null) {
                continue;
            }
            for (Permission permission : permissionsOf(assignment.getRole().getName())) {
                grants.merge(permission, scope, PermissionScope::merge);
            }
        }
    }

    /**
     * Department id -> itself plus all descendants (active departments only).
     */
    private Map<UUID, Set<UUID>> descendants(UUID tenantId) {
        List<Department> departments = departmentRepository.findByTenantIdOrderByPath(tenantId).stream()
                .filter(d -> d.getStatus() == com.telt.constants.RecordStatus.ACTIVE)
                .toList();
        Map<UUID, Set<UUID>> result = new HashMap<>();
        for (Department department : departments) {
            result.put(department.getDepartmentId(), departments.stream()
                    .filter(d -> d.getPath().startsWith(department.getPath()))
                    .map(Department::getDepartmentId)
                    .collect(Collectors.toUnmodifiableSet()));
        }
        return result;
    }

    private Map<String, Set<Permission>> rolePermissions() {
        Map<String, Set<Permission>> cached = rolePermissions;
        if (cached == null) {
            cached = roleRepository.findAll().stream()
                    .collect(Collectors.toUnmodifiableMap(Role::getName, r -> Set.copyOf(r.getPermissions())));
            rolePermissions = cached;
        }
        return cached;
    }

    public static boolean isStaff(MembershipType type) {
        return type == MembershipType.TENANT_ADMIN || type == MembershipType.SUB_ADMIN;
    }

    public static boolean isScopedAssignment(ScopeType type) {
        return type != ScopeType.TENANT;
    }
}
