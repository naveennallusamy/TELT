package com.telt.repository;

import com.telt.entity.exam.ExamVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface ExamVersionRepository extends JpaRepository<ExamVersion, UUID> {

    Optional<ExamVersion> findByTenantIdAndExamIdAndVersionNo(UUID tenantId, UUID examId, int versionNo);

    List<ExamVersion> findByTenantIdAndExamIdOrderByVersionNo(UUID tenantId, UUID examId);
}
