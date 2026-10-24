package com.telt.repository;

import com.telt.entity.exam.ExamAccommodation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface ExamAccommodationRepository extends JpaRepository<ExamAccommodation, UUID> {

    Optional<ExamAccommodation> findByTenantIdAndScheduleIdAndUserId(UUID tenantId, UUID scheduleId, UUID userId);

    List<ExamAccommodation> findByTenantIdAndScheduleIdOrderByGrantedAt(UUID tenantId, UUID scheduleId);
}
