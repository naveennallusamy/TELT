package com.telt.controller;

import com.telt.dto.exam.ExamResponse;
import com.telt.dto.schedule.DistributeExamRequest;
import com.telt.dto.schedule.DistributionResponse;
import com.telt.dto.schedule.ScheduleResponse;
import com.telt.entity.exam.OverridePolicy;
import com.telt.service.PlatformExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/platform")
@RequiredArgsConstructor
public class PlatformExamController {

    private final PlatformExamService platformExamService;

    @PostMapping("/exams/{examId}/distributions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SUPER_ADMIN') and hasPermission(null, 'PLATFORM_EXAM_DISTRIBUTE')")
    public DistributionResponse distribute(@PathVariable UUID examId, @Valid @RequestBody DistributeExamRequest request) {
        return platformExamService.distribute(examId, request);
    }

    @GetMapping("/exams/{examId}/distributions")
    @PreAuthorize("hasAuthority('SUPER_ADMIN') and hasPermission(null, 'PLATFORM_EXAM_DISTRIBUTE')")
    public List<DistributionResponse> distributions(@PathVariable UUID examId) {
        return platformExamService.distributions(examId);
    }

    @PutMapping("/distributions/{distributionId}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN') and hasPermission(null, 'PLATFORM_EXAM_DISTRIBUTE')")
    public DistributionResponse updatePolicy(@PathVariable UUID distributionId, @RequestBody OverridePolicy policy) {
        return platformExamService.updatePolicy(distributionId, policy);
    }

    @PostMapping("/distributions/{distributionId}/revoke")
    @PreAuthorize("hasAuthority('SUPER_ADMIN') and hasPermission(null, 'PLATFORM_EXAM_DISTRIBUTE')")
    public DistributionResponse revoke(@PathVariable UUID distributionId) {
        return platformExamService.revoke(distributionId);
    }

    @GetMapping("/tenants/{tenantId}/exams")
    @PreAuthorize("hasAuthority('SUPER_ADMIN') and hasPermission(null, 'PLATFORM_BREAK_GLASS')")
    public List<ExamResponse> tenantExams(@PathVariable UUID tenantId) {
        return platformExamService.tenantExams(tenantId);
    }

    @GetMapping("/tenants/{tenantId}/exam-schedules")
    @PreAuthorize("hasAuthority('SUPER_ADMIN') and hasPermission(null, 'PLATFORM_BREAK_GLASS')")
    public List<ScheduleResponse> tenantSchedules(@PathVariable UUID tenantId) {
        return platformExamService.tenantSchedules(tenantId);
    }
}
