package com.telt.dto.exam;

import com.telt.constants.RecordStatus;
import com.telt.entity.exam.Exam;

import java.util.UUID;

public record ExamResponse(UUID examId, UUID departmentId, String code, String title, String description,
                           RecordStatus status, int latestVersion, Integer publishedVersion,
                           UUID sourceExamId) {

    public static ExamResponse from(Exam e) {
        return new ExamResponse(e.getExamId(), e.getDepartmentId(), e.getCode(), e.getTitle(), e.getDescription(),
                e.getStatus(), e.getLatestVersion(), e.getPublishedVersion(), e.getSourceExamId());
    }
}
