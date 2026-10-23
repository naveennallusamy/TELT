package com.telt.dto.schedule;

import com.telt.constants.ScheduleStatus;
import com.telt.entity.exam.ExamSchedule;
import com.telt.entity.exam.ExamSettings;
import com.telt.entity.exam.ScheduleOverrides;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code effectiveSettings} is the exam version's settings with this schedule's overrides applied.
 */
public record ScheduleResponse(UUID scheduleId, UUID examId, int versionNo, String title, Instant startsAt,
                               Instant endsAt, String timeZone, int lateEntryMinutes, ScheduleOverrides overrides,
                               ExamSettings effectiveSettings, ScheduleStatus status) {

    public static ScheduleResponse from(ExamSchedule s, ExamSettings base) {
        return new ScheduleResponse(s.getScheduleId(), s.getExamId(), s.getVersionNo(), s.getTitle(), s.getStartsAt(),
                s.getEndsAt(), s.getTimeZone(), s.getLateEntryMinutes(), s.getOverrides(),
                s.getOverrides().applyTo(base), s.getStatus());
    }
}
