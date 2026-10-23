package com.telt.repository;

import com.telt.entity.exam.ExamSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface ExamSectionRepository extends JpaRepository<ExamSection, UUID> {

    List<ExamSection> findByTenantIdAndExamIdAndVersionNoOrderByPosition(UUID tenantId, UUID examId, int versionNo);
}
