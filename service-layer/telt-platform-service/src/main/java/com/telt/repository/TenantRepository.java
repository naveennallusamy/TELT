package com.telt.repository;

import com.telt.entity.tenant.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findByTenantName(String name);

    boolean existsByTenantNameIgnoreCase(String name);
}
