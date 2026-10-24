package com.telt.controller;

import com.telt.dto.report.PlatformExamReport;
import com.telt.dto.report.ScheduleReport;
import com.telt.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReportController {

    private static final MediaType CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final ReportService reportService;

    @GetMapping("/exam-schedules/{scheduleId}/report")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ScheduleReport schedule(@PathVariable UUID scheduleId) {
        return reportService.schedule(scheduleId);
    }

    @GetMapping("/exam-schedules/{scheduleId}/report.csv")
    @PreAuthorize("hasPermission(null, 'REPORT_EXPORT')")
    public ResponseEntity<String> export(@PathVariable UUID scheduleId) {
        return ResponseEntity.ok().contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("schedule-" + scheduleId + ".csv").build().toString())
                .body(reportService.exportCsv(scheduleId));
    }

    @GetMapping("/platform/tenants/{tenantId}/exam-schedules/{scheduleId}/report")
    @PreAuthorize("hasAuthority('SUPER_ADMIN') and hasPermission(null, 'PLATFORM_BREAK_GLASS')")
    public ScheduleReport tenantSchedule(@PathVariable UUID tenantId, @PathVariable UUID scheduleId) {
        return reportService.tenantSchedule(tenantId, scheduleId);
    }

    @GetMapping("/platform/exams/{examId}/report")
    @PreAuthorize("hasAuthority('SUPER_ADMIN') and hasPermission(null, 'PLATFORM_BREAK_GLASS')")
    public PlatformExamReport platformExam(@PathVariable UUID examId) {
        return reportService.platformExam(examId);
    }
}
