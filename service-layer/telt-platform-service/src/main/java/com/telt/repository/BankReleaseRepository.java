package com.telt.repository;

import com.telt.entity.question.BankRelease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface BankReleaseRepository extends JpaRepository<BankRelease, UUID> {

    List<BankRelease> findByBankIdOrderByReleaseNoDesc(UUID bankId);

    Optional<BankRelease> findFirstByBankIdOrderByReleaseNoDesc(UUID bankId);

    Optional<BankRelease> findByReleaseIdAndBankId(UUID releaseId, UUID bankId);
}
