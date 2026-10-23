package com.telt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for tenant-owned entities exposing {@code id} and {@code tenant}. {@link #findAll()} and
 * {@link #findById(Object)} only return rows of the caller's tenant (super admins see all). Callers without a
 * tenant see nothing.
 */
@NoRepositoryBean
public interface BaseRepository<T, ID> extends JpaRepository<T, ID> {

    String TENANT_SCOPE = "(:#{T(com.telt.util.TenantContext).isSuperAdmin()} = true OR "
            + "e.tenant.tenantId = :#{T(com.telt.util.TenantContext).getCurrentTenant()})";

    @Override
    @Query("select e from #{#entityName} e where " + TENANT_SCOPE)
    List<T> findAll();

    @Override
    @Query("select e from #{#entityName} e where e.id = :id and " + TENANT_SCOPE)
    Optional<T> findById(@Param("id") ID id);
}
