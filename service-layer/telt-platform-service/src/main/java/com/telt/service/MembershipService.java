package com.telt.service;

import com.telt.access.AuthorizationService;
import com.telt.access.AuthorizationSnapshot;
import com.telt.access.PermissionScope;
import com.telt.constants.*;
import com.telt.dto.access.*;
import com.telt.dto.user.MembershipSummary;
import com.telt.entity.role.Role;
import com.telt.entity.role.RoleAssignment;
import com.telt.entity.tenant.Tenant;
import com.telt.entity.tenant.TenantMembership;
import com.telt.entity.user.User;
import com.telt.error.ApiException;
import com.telt.repository.*;
import com.telt.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tenant memberships, sub-admin access mode and scoped role assignments.
 */
@Service
@RequiredArgsConstructor
public class MembershipService {

    private final TenantMembershipRepository membershipRepository;
    private final RoleAssignmentRepository assignmentRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<MembershipSummary> summariesFor(UUID userId) {
        List<TenantMembership> memberships = membershipRepository.findByUser_Id(userId);
        Map<UUID, String> names = tenantRepository.findAllById(
                        memberships.stream().map(TenantMembership::getTenantId).toList()).stream()
                .collect(Collectors.toMap(Tenant::getTenantId, Tenant::getTenantName));
        return memberships.stream()
                .map(m -> new MembershipSummary(m.getTenantId(), names.get(m.getTenantId()), m.getMembershipType(),
                        m.getAccessMode(), m.getDepartmentId(), m.getStatus()))
                .sorted(Comparator.comparing(MembershipSummary::tenantName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    @Transactional
    public TenantMembership createMembership(User user, Tenant tenant, MembershipType type, AccessMode accessMode,
                                             UUID departmentId) {
        List<TenantMembership> existing = membershipRepository.findByUser_Id(user.getId());
        if (existing.stream().anyMatch(m -> m.getTenantId().equals(tenant.getTenantId()))) {
            throw ApiException.conflict("User is already a member of this tenant");
        }
        boolean isStudentElsewhere = existing.stream().anyMatch(m -> m.getMembershipType() == MembershipType.STUDENT);
        if (isStudentElsewhere || (type == MembershipType.STUDENT && !existing.isEmpty())) {
            throw ApiException.conflict("A student can belong to only one tenant");
        }
        if (type != MembershipType.SUB_ADMIN && accessMode != null) {
            throw ApiException.badRequest("accessMode applies to sub-admins only");
        }
        requireDepartment(tenant.getTenantId(), departmentId);
        TenantMembership membership = new TenantMembership();
        membership.setTenantId(tenant.getTenantId());
        membership.setUser(user);
        membership.setMembershipType(type);
        membership.setAccessMode(type == MembershipType.SUB_ADMIN
                ? Objects.requireNonNullElse(accessMode, tenant.getSubAdminDefaultAccessMode()) : null);
        membership.setDepartmentId(departmentId);
        membershipRepository.saveAndFlush(membership);
        auditService.record(tenant.getTenantId(), "MEMBER_ADDED", "USER", user.getId(),
                Map.of("membershipType", type, "accessMode", String.valueOf(membership.getAccessMode()),
                        "departmentId", String.valueOf(departmentId)));
        authorizationService.evictUser(tenant.getTenantId(), user.getId());
        return membership;
    }

    /**
     * Memberships of the current tenant visible to the caller under {@code permission}.
     */
    @Transactional(readOnly = true)
    public List<TenantMembership> visibleMemberships(Permission permission) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        PermissionScope scope = authorizationService.current().scope(permission)
                .orElseThrow(() -> ApiException.forbidden("Access denied"));
        return scope.tenantWide() ? membershipRepository.findByTenantId(tenantId)
                : membershipRepository.findByTenantIdAndDepartmentIdIn(tenantId, scope.departmentIds());
    }

    /**
     * Membership of {@code userId} in the current tenant if the caller holds {@code permission} for it. Callers
     * without the permission tenant-wide may only act on students in their departments.
     */
    @Transactional(readOnly = true)
    public TenantMembership requireAccessible(UUID userId, Permission permission) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        AuthorizationSnapshot snapshot = authorizationService.current();
        TenantMembership membership = membershipRepository.findByTenantIdAndUser_Id(tenantId, userId)
                .filter(m -> snapshot.allowsDepartment(permission, m.getDepartmentId()))
                .orElseThrow(() -> ApiException.notFound("User"));
        if (permission == Permission.USER_MANAGE && !snapshot.hasTenantWide(permission)
                && membership.getMembershipType() != MembershipType.STUDENT) {
            throw ApiException.forbidden("Department-scoped administrators can only manage students");
        }
        return membership;
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers() {
        return visibleMemberships(Permission.USER_VIEW).stream().map(this::toMember).toList();
    }

    @Transactional
    public MemberResponse addExisting(AddMemberRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        if (request.membershipType() == MembershipType.STUDENT) {
            throw ApiException.badRequest("Create students with POST /user/register");
        }
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> ApiException.notFound("User"));
        if (user.isSuperAdmin()) {
            throw ApiException.badRequest("Super admins cannot be tenant members");
        }
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> ApiException.notFound("Tenant"));
        return toMember(createMembership(user, tenant, request.membershipType(), request.accessMode(),
                request.departmentId()));
    }

    @Transactional
    public MemberResponse update(UUID userId, UpdateMemberRequest request) {
        TenantMembership membership = requireAccessible(userId, Permission.USER_MANAGE);
        AuthorizationSnapshot snapshot = authorizationService.current();
        Map<String, Object> changes = new LinkedHashMap<>();
        if (request.accessMode() != null && request.accessMode() != membership.getAccessMode()) {
            authorizationService.require(() -> snapshot.hasTenantWide(Permission.USER_ACCESS_MODE_MANAGE));
            if (membership.getMembershipType() != MembershipType.SUB_ADMIN) {
                throw ApiException.badRequest("Access mode applies to sub-admins only");
            }
            if (request.accessMode() == AccessMode.DEPARTMENT_SCOPED && assignmentRepository
                    .existsByTenantIdAndUserIdAndScopeType(membership.getTenantId(), userId, ScopeType.TENANT)) {
                throw ApiException.conflict(
                        "Revoke tenant-wide role assignments before switching to department-scoped access");
            }
            membership.setAccessMode(request.accessMode());
            changes.put("accessMode", request.accessMode());
        }
        if (request.departmentId() != null && !request.departmentId().equals(membership.getDepartmentId())) {
            requireDepartment(membership.getTenantId(), request.departmentId());
            authorizationService.require(() -> snapshot.allowsDepartment(Permission.USER_MANAGE, request.departmentId()));
            membership.setDepartmentId(request.departmentId());
            changes.put("departmentId", request.departmentId());
        }
        if (!changes.isEmpty()) {
            auditService.record(membership.getTenantId(), "MEMBER_UPDATED", "USER", userId, changes);
            authorizationService.evictUser(membership.getTenantId(), userId);
        }
        return toMember(membership);
    }

    @Transactional
    public void setStatus(UUID userId, RecordStatus status) {
        TenantMembership membership = requireAccessible(userId, Permission.USER_MANAGE);
        membership.setStatus(status);
        auditService.record(membership.getTenantId(), "MEMBER_STATUS_CHANGED", "USER", userId, Map.of("status", status));
        authorizationService.evictUser(membership.getTenantId(), userId);
    }

    @Transactional(readOnly = true)
    public List<RoleAssignmentResponse> assignments(UUID userId) {
        TenantMembership membership = requireAccessible(userId, Permission.USER_VIEW);
        return assignmentRepository.findByTenantIdAndUserId(membership.getTenantId(), userId).stream()
                .map(RoleAssignmentResponse::from).toList();
    }

    @Transactional
    public RoleAssignmentResponse assign(UUID userId, AssignRoleRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        AuthorizationSnapshot snapshot = authorizationService.current();
        if (userId.equals(snapshot.userId())) {
            throw ApiException.forbidden("You cannot assign roles to yourself");
        }
        TenantMembership membership = membershipRepository.findByTenantIdAndUser_Id(tenantId, userId)
                .orElseThrow(() -> ApiException.notFound("User"));
        if (membership.getMembershipType() != MembershipType.SUB_ADMIN) {
            throw ApiException.badRequest("Roles can only be assigned to sub-admins");
        }
        Role role = roleRepository.findByName(request.role()).filter(Role::isAssignable)
                .orElseThrow(() -> ApiException.badRequest("Unknown or non-assignable role: " + request.role()));
        RoleAssignment assignment = new RoleAssignment();
        assignment.setTenantId(tenantId);
        assignment.setUserId(userId);
        assignment.setRole(role);
        assignment.setScopeType(request.scopeType());
        switch (request.scopeType()) {
            case TENANT -> {
                if (request.departmentId() != null) {
                    throw ApiException.badRequest("departmentId must be empty for TENANT scope");
                }
                if (membership.getAccessMode() != AccessMode.TENANT_WIDE) {
                    throw ApiException.badRequest("Department-scoped sub-admins can only receive department roles");
                }
                requireGrantable(role, p -> snapshot.hasTenantWide(p));
            }
            case DEPARTMENT -> {
                if (request.departmentId() == null) {
                    throw ApiException.badRequest("departmentId is required for DEPARTMENT scope");
                }
                requireDepartment(tenantId, request.departmentId());
                assignment.setDepartmentId(request.departmentId());
                requireGrantable(role, p -> snapshot.allowsDepartment(p, request.departmentId()));
            }
            case RESOURCE -> throw ApiException.badRequest(
                    "Resource-scoped assignments become available with exams and question banks");
        }
        assignment.setGrantedBy(snapshot.userId().toString());
        assignment.setGrantedAt(Instant.now());
        assignmentRepository.saveAndFlush(assignment);
        auditService.record(tenantId, "ROLE_ASSIGNED", "USER", userId, Map.of("role", role.getName(),
                "scopeType", request.scopeType(), "departmentId", String.valueOf(request.departmentId())));
        authorizationService.evictUser(tenantId, userId);
        return RoleAssignmentResponse.from(assignment);
    }

    @Transactional
    public void revoke(UUID userId, UUID assignmentId) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        AuthorizationSnapshot snapshot = authorizationService.current();
        RoleAssignment assignment = assignmentRepository.findByAssignmentIdAndTenantIdAndUserId(assignmentId, tenantId, userId)
                .orElseThrow(() -> ApiException.notFound("Role assignment"));
        boolean allowed = assignment.getScopeType() == ScopeType.DEPARTMENT
                ? snapshot.allowsDepartment(Permission.ROLE_ASSIGN, assignment.getDepartmentId())
                : snapshot.hasTenantWide(Permission.ROLE_ASSIGN);
        authorizationService.require(() -> allowed);
        assignmentRepository.delete(assignment);
        auditService.record(tenantId, "ROLE_REVOKED", "USER", userId, Map.of("role", assignment.getRole().getName(),
                "scopeType", assignment.getScopeType(), "departmentId", String.valueOf(assignment.getDepartmentId())));
        authorizationService.evictUser(tenantId, userId);
    }

    /**
     * Grantors need ROLE_ASSIGN for the scope and must themselves hold every permission of the role there.
     */
    private void requireGrantable(Role role, Function<Permission, Boolean> holds) {
        if (!holds.apply(Permission.ROLE_ASSIGN)) {
            throw ApiException.forbidden("Access denied");
        }
        List<Permission> missing = authorizationService.permissionsOf(role.getName()).stream()
                .filter(p -> !holds.apply(p)).sorted().toList();
        if (!missing.isEmpty()) {
            throw ApiException.forbidden("You cannot grant permissions you do not hold: " + missing);
        }
    }

    private void requireDepartment(UUID tenantId, UUID departmentId) {
        if (departmentId != null && departmentRepository.findByDepartmentIdAndTenantId(departmentId, tenantId)
                .filter(d -> d.getStatus() == RecordStatus.ACTIVE).isEmpty()) {
            throw ApiException.notFound("Department");
        }
    }

    private MemberResponse toMember(TenantMembership m) {
        User user = m.getUser();
        return new MemberResponse(user.getId(), user.getEmail(), user.getUsername(), m.getMembershipType(),
                m.getAccessMode(), m.getDepartmentId(), m.getStatus(),
                assignmentRepository.findByTenantIdAndUserId(m.getTenantId(), user.getId()).stream()
                        .map(RoleAssignmentResponse::from).toList());
    }
}
