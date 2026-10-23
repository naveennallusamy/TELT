package com.telt.repository;

import org.springframework.transaction.annotation.Transactional;

import com.telt.constants.ScopeType;
import com.telt.entity.role.RoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, UUID> {

    List<RoleAssignment> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    Optional<RoleAssignment> findByAssignmentIdAndTenantIdAndUserId(UUID assignmentId, UUID tenantId, UUID userId);

    boolean existsByTenantIdAndUserIdAndScopeType(UUID tenantId, UUID userId, ScopeType scopeType);

    boolean existsByTenantIdAndDepartmentId(UUID tenantId, UUID departmentId);
}
