package com.telt.dto.schedule;

import com.telt.entity.exam.ExamAssignment;

import java.time.Instant;
import java.util.UUID;

public record AssignmentResponse(UUID assignmentId, UUID userId, UUID departmentId, UUID assignedBy,
                                 Instant assignedAt) {

    public static AssignmentResponse from(ExamAssignment a) {
        return new AssignmentResponse(a.getAssignmentId(), a.getUserId(), a.getDepartmentId(), a.getAssignedBy(),
                a.getAssignedAt());
    }
}
