package com.telt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

@NoRepositoryBean
public interface BaseRepository<T, ID> extends JpaRepository<T, ID> {

    @Override
    @Query("select e from #{#entityName} e where " +
            "(?#{T(com.example.TenantContext).isSuperAdmin()} = true OR " +
            " e.tenantId = ?#{T(com.example.TenantContext).getCurrentTenant()})")
    List<T> findAll();
}

