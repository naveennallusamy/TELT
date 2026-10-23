package com.telt.repository;

import com.telt.entity.exam.ExamDistribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface ExamDistributionRepository extends JpaRepository<ExamDistribution, UUID> {

    Optional<ExamDistribution> findByTenantIdAndSourceExamId(UUID tenantId, UUID sourceExamId);

    Optional<ExamDistribution> findByTenantIdAndExamId(UUID tenantId, UUID examId);

    List<ExamDistribution> findBySourceExamIdOrderByDistributedAt(UUID sourceExamId);
}
