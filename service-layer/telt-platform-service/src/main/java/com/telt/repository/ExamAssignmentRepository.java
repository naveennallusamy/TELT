package com.telt.repository;

import com.telt.entity.exam.ExamAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface ExamAssignmentRepository extends JpaRepository<ExamAssignment, UUID> {

    List<ExamAssignment> findByTenantIdAndScheduleIdOrderByAssignedAt(UUID tenantId, UUID scheduleId);

    Optional<ExamAssignment> findByAssignmentIdAndTenantIdAndScheduleId(UUID assignmentId, UUID tenantId, UUID scheduleId);

    boolean existsByTenantIdAndScheduleIdAndUserId(UUID tenantId, UUID scheduleId, UUID userId);

    boolean existsByTenantIdAndScheduleIdAndDepartmentId(UUID tenantId, UUID scheduleId, UUID departmentId);

    @Query("select distinct a.scheduleId from ExamAssignment a where a.tenantId = :tenantId "
            + "and (a.userId = :userId or a.departmentId in :departmentIds)")
    List<UUID> findScheduleIdsFor(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId,
                                  @Param("departmentIds") Collection<UUID> departmentIds);
}
