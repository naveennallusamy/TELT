package com.telt.repository;

import com.telt.constants.ScheduleStatus;
import com.telt.entity.exam.ExamSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface ExamScheduleRepository extends JpaRepository<ExamSchedule, UUID> {

    Optional<ExamSchedule> findByScheduleIdAndTenantId(UUID scheduleId, UUID tenantId);

    List<ExamSchedule> findByTenantIdOrderByStartsAt(UUID tenantId);

    List<ExamSchedule> findByTenantIdAndExamIdOrderByStartsAt(UUID tenantId, UUID examId);

    List<ExamSchedule> findByTenantIdAndScheduleIdInAndStatusOrderByStartsAt(UUID tenantId, Collection<UUID> ids,
                                                                            ScheduleStatus status);

    long countByTenantIdAndExamIdAndStatus(UUID tenantId, UUID examId, ScheduleStatus status);
}
