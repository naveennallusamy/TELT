package com.telt.repository;

import com.telt.constants.GrantStatus;
import com.telt.entity.question.PlatformBankGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PlatformBankGrantRepository extends JpaRepository<PlatformBankGrant, UUID> {

    Optional<PlatformBankGrant> findByGrantIdAndTenantId(UUID grantId, UUID tenantId);

    List<PlatformBankGrant> findByTenantIdAndStatusOrderByCreatedOn(UUID tenantId, GrantStatus status);

    List<PlatformBankGrant> findAllByOrderByCreatedOn();

    boolean existsByTenantIdAndBankIdAndStatus(UUID tenantId, UUID bankId, GrantStatus status);
}
