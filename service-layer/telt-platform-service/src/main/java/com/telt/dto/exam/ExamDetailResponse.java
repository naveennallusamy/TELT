package com.telt.dto.exam;

import java.util.List;

public record ExamDetailResponse(ExamResponse exam, List<ExamVersionResponse> versions) {
}
