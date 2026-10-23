package com.telt.service;

import com.telt.access.AuthorizationService;
import com.telt.constants.RecordStatus;
import com.telt.dto.department.CreateDepartmentRequest;
import com.telt.dto.department.DepartmentResponse;
import com.telt.dto.department.UpdateDepartmentRequest;
import com.telt.entity.tenant.Department;
import com.telt.error.ApiException;
import com.telt.repository.DepartmentRepository;
import com.telt.repository.RoleAssignmentRepository;
import com.telt.repository.TenantMembershipRepository;
import com.telt.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final TenantMembershipRepository membershipRepository;
    private final RoleAssignmentRepository assignmentRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<DepartmentResponse> list() {
        return departmentRepository.findByTenantIdOrderByPath(TenantContext.requireCurrentTenant()).stream()
                .map(DepartmentResponse::from).toList();
    }

    @Transactional
    public DepartmentResponse create(CreateDepartmentRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        if (departmentRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, request.code())) {
            throw ApiException.conflict("Department code already exists");
        }
        String parentPath = "/";
        if (request.parentDepartmentId() != null) {
            Department parent = load(tenantId, request.parentDepartmentId());
            if (parent.getStatus() != RecordStatus.ACTIVE) {
                throw ApiException.badRequest("Parent department is inactive");
            }
            parentPath = parent.getPath();
        }
        Department department = new Department();
        department.setTenantId(tenantId);
        department.setParentDepartmentId(request.parentDepartmentId());
        department.setCode(request.code());
        department.setName(request.name());
        department.setPath(parentPath);
        departmentRepository.saveAndFlush(department);
        department.setPath(parentPath + department.getDepartmentId() + "/");
        auditService.record(tenantId, "DEPARTMENT_CREATED", "DEPARTMENT", department.getDepartmentId(),
                Map.of("code", request.code(), "parentDepartmentId", String.valueOf(request.parentDepartmentId())));
        authorizationService.evictTenant(tenantId);
        return DepartmentResponse.from(department);
    }

    @Transactional
    public DepartmentResponse update(UUID departmentId, UpdateDepartmentRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        Department department = load(tenantId, departmentId);
        Map<String, Object> changes = new LinkedHashMap<>();
        if (request.name() != null) {
            department.setName(request.name());
            changes.put("name", request.name());
        }
        if (request.status() != null && request.status() != department.getStatus()) {
            department.setStatus(request.status());
            changes.put("status", request.status());
        }
        if (!changes.isEmpty()) {
            auditService.record(tenantId, "DEPARTMENT_UPDATED", "DEPARTMENT", departmentId, changes);
            authorizationService.evictTenant(tenantId);
        }
        return DepartmentResponse.from(department);
    }

    @Transactional
    public void delete(UUID departmentId) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        Department department = load(tenantId, departmentId);
        if (departmentRepository.existsByTenantIdAndParentDepartmentId(tenantId, departmentId)
                || membershipRepository.existsByTenantIdAndDepartmentId(tenantId, departmentId)
                || assignmentRepository.existsByTenantIdAndDepartmentId(tenantId, departmentId)) {
            throw ApiException.conflict("Department has sub-departments, members or role assignments; deactivate it instead");
        }
        departmentRepository.delete(department);
        auditService.record(tenantId, "DEPARTMENT_DELETED", "DEPARTMENT", departmentId, Map.of("code", department.getCode()));
        authorizationService.evictTenant(tenantId);
    }

    private Department load(UUID tenantId, UUID departmentId) {
        return departmentRepository.findByDepartmentIdAndTenantId(departmentId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Department"));
    }
}
