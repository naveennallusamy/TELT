package com.telt.repository;

import org.springframework.transaction.annotation.Transactional;

import com.telt.entity.tenant.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    List<Department> findByTenantIdOrderByPath(UUID tenantId);

    Optional<Department> findByDepartmentIdAndTenantId(UUID departmentId, UUID tenantId);

    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

    boolean existsByTenantIdAndParentDepartmentId(UUID tenantId, UUID parentDepartmentId);
}
