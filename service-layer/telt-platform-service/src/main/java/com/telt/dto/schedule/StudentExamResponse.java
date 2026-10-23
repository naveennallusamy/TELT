package com.telt.dto.schedule;

import com.telt.constants.ResultRelease;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What a student sees before starting: the window and effective settings, but no questions.
 */
public record StudentExamResponse(UUID scheduleId, UUID examId, String examTitle, String scheduleTitle,
                                  Instant startsAt, Instant endsAt, String timeZone, int lateEntryMinutes,
                                  int durationMinutes, int maxAttempts, ResultRelease resultRelease,
                                  String instructions, BigDecimal totalMarks, List<Section> sections) {

    public record Section(String title, Integer timeLimitMinutes, int questionCount) {
    }
}
