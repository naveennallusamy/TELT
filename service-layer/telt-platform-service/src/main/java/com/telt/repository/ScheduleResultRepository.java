package com.telt.repository;

import com.telt.entity.exam.ScheduleResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface ScheduleResultRepository extends JpaRepository<ScheduleResult, UUID> {

    Optional<ScheduleResult> findByScheduleIdAndTenantId(UUID scheduleId, UUID tenantId);
}
