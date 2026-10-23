package com.telt.repository;

import com.telt.entity.question.QuestionVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface QuestionVersionRepository extends JpaRepository<QuestionVersion, UUID> {

    List<QuestionVersion> findByTenantIdAndQuestionIdOrderByVersionNo(UUID tenantId, UUID questionId);

    Optional<QuestionVersion> findByTenantIdAndQuestionIdAndVersionNo(UUID tenantId, UUID questionId, int versionNo);
}
