package com.telt.dto.exam;

import com.telt.constants.ExamVersionStatus;
import com.telt.entity.exam.ExamSettings;
import com.telt.entity.exam.ExamVersion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExamVersionResponse(int versionNo, ExamVersionStatus status, ExamSettings settings,
                                  BigDecimal totalMarks, List<SectionResponse> sections, UUID authoredBy,
                                  Instant createdOn, Instant submittedAt, UUID reviewedBy, Instant reviewedAt,
                                  UUID publishedBy, Instant publishedAt) {

    public static ExamVersionResponse from(ExamVersion v, List<SectionResponse> sections) {
        return new ExamVersionResponse(v.getVersionNo(), v.getStatus(), v.getSettings(), v.getTotalMarks(), sections,
                v.getAuthoredBy(), v.getCreatedOn(), v.getSubmittedAt(), v.getReviewedBy(), v.getReviewedAt(),
                v.getPublishedBy(), v.getPublishedAt());
    }
}
