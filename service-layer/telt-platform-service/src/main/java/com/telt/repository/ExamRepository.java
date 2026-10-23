package com.telt.repository;

import com.telt.entity.exam.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface ExamRepository extends JpaRepository<Exam, UUID> {

    Optional<Exam> findByExamIdAndTenantId(UUID examId, UUID tenantId);

    List<Exam> findByTenantIdOrderByCode(UUID tenantId);

    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
}
