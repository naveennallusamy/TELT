package com.telt.repository;

import com.telt.constants.MembershipType;
import com.telt.entity.tenant.TenantMembership;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, UUID> {

    Optional<TenantMembership> findByTenantIdAndUser_Id(UUID tenantId, UUID userId);

    List<TenantMembership> findByUser_Id(UUID userId);

    @EntityGraph(attributePaths = "user")
    List<TenantMembership> findByTenantId(UUID tenantId);

    @EntityGraph(attributePaths = "user")
    List<TenantMembership> findByTenantIdAndDepartmentIdIn(UUID tenantId, Collection<UUID> departmentIds);

    boolean existsByUser_IdAndMembershipType(UUID userId, MembershipType membershipType);

    boolean existsByTenantIdAndDepartmentId(UUID tenantId, UUID departmentId);
}
