package com.telt.repository;

import com.telt.entity.exam.AttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Transactional(readOnly = true)
public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, UUID> {

    List<AttemptAnswer> findByTenantIdAndAttemptIdOrderBySectionIndexAscPositionAsc(UUID tenantId, UUID attemptId);
}
