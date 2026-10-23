package com.telt.repository;

import com.telt.constants.AttemptStatus;
import com.telt.entity.exam.ExamAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, UUID> {

    Optional<ExamAttempt> findByAttemptIdAndTenantId(UUID attemptId, UUID tenantId);

    Optional<ExamAttempt> findByTenantIdAndScheduleIdAndUserIdAndStatus(UUID tenantId, UUID scheduleId, UUID userId,
                                                                        AttemptStatus status);

    long countByTenantIdAndScheduleIdAndUserId(UUID tenantId, UUID scheduleId, UUID userId);

    List<ExamAttempt> findByTenantIdAndUserIdOrderByStartedAtDesc(UUID tenantId, UUID userId);

    List<ExamAttempt> findByTenantIdAndScheduleIdOrderByStartedAt(UUID tenantId, UUID scheduleId);
}
