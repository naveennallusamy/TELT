package com.telt.repository;

import com.telt.entity.Tenant;

import java.util.Optional;

public interface TenantRepository extends BaseRepository<Tenant, Long> {
    Optional<Tenant> findByName(String name);
}
