package com.telt.controller;

import com.telt.dto.attempt.*;
import com.telt.service.AttemptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AttemptController {

    private final AttemptService attemptService;

    @PostMapping("/exam-schedules/{scheduleId}/attempts")
    @PreAuthorize("hasPermission(null, 'ATTEMPT_OWN')")
    public AttemptResponse start(@PathVariable UUID scheduleId) {
        return attemptService.start(scheduleId);
    }

    @GetMapping("/exam-schedules/{scheduleId}/attempts")
    @PreAuthorize("hasPermission(null, 'RESPONSE_VIEW') or hasPermission(null, 'EXAM_MONITOR_LIVE')")
    public List<AttemptSummary> forSchedule(@PathVariable UUID scheduleId) {
        return attemptService.forSchedule(scheduleId);
    }

    @PutMapping("/exam-schedules/{scheduleId}/accommodations/{userId}")
    @PreAuthorize("hasPermission(null, 'ATTEMPT_ACCOMMODATE')")
    public com.telt.dto.result.AccommodationResponse accommodate(@PathVariable UUID scheduleId, @PathVariable UUID userId,
                                                                 @Valid @RequestBody com.telt.dto.result.AccommodationRequest request) {
        return attemptService.accommodate(scheduleId, userId, request);
    }

    @GetMapping("/exam-schedules/{scheduleId}/accommodations")
    @PreAuthorize("hasPermission(null, 'ATTEMPT_ACCOMMODATE') or hasPermission(null, 'EXAM_MONITOR_LIVE')")
    public List<com.telt.dto.result.AccommodationResponse> accommodations(@PathVariable UUID scheduleId) {
        return attemptService.accommodations(scheduleId);
    }

    @GetMapping("/me/attempts")
    @PreAuthorize("hasPermission(null, 'ATTEMPT_OWN')")
    public List<AttemptResponse> mine() {
        return attemptService.mine();
    }

    @GetMapping("/me/attempts/{attemptId}")
    @PreAuthorize("hasPermission(null, 'ATTEMPT_OWN')")
    public AttemptResponse get(@PathVariable UUID attemptId) {
        return attemptService.get(attemptId);
    }

    @PutMapping("/me/attempts/{attemptId}/answers")
    @PreAuthorize("hasPermission(null, 'ATTEMPT_OWN')")
    public AttemptResponse save(@PathVariable UUID attemptId, @Valid @RequestBody SaveAnswersRequest request) {
        return attemptService.save(attemptId, request);
    }

    @PostMapping("/me/attempts/{attemptId}/next-section")
    @PreAuthorize("hasPermission(null, 'ATTEMPT_OWN')")
    public AttemptResponse nextSection(@PathVariable UUID attemptId) {
        return attemptService.nextSection(attemptId);
    }

    @PostMapping("/me/attempts/{attemptId}/submit")
    @PreAuthorize("hasPermission(null, 'ATTEMPT_OWN')")
    public AttemptResponse submit(@PathVariable UUID attemptId) {
        return attemptService.submit(attemptId);
    }

    @GetMapping("/attempts/{attemptId}")
    @PreAuthorize("hasPermission(null, 'RESPONSE_VIEW')")
    public AttemptReviewResponse review(@PathVariable UUID attemptId) {
        return attemptService.review(attemptId);
    }

    @PutMapping("/attempts/{attemptId}/answers/{snapshotId}/grade")
    @PreAuthorize("hasPermission(null, 'GRADE_DESCRIPTIVE')")
    public AttemptReviewResponse grade(@PathVariable UUID attemptId, @PathVariable UUID snapshotId,
                                       @Valid @RequestBody GradeRequest request) {
        return attemptService.grade(attemptId, snapshotId, request);
    }
}
