package com.telt.controller;

import com.telt.dto.result.ResultDecisionRequest;
import com.telt.dto.result.ScheduleResultResponse;
import com.telt.dto.result.StudentResultResponse;
import com.telt.service.ResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ResultController {

    private final ResultService resultService;

    @GetMapping("/exam-schedules/{scheduleId}/results")
    @PreAuthorize("hasPermission(null, 'RESULT_SUBMIT_FOR_APPROVAL') or hasPermission(null, 'RESULT_APPROVE') "
            + "or hasPermission(null, 'RESULT_PUBLISH') or hasPermission(null, 'RESPONSE_VIEW')")
    public ScheduleResultResponse status(@PathVariable UUID scheduleId) {
        return resultService.status(scheduleId);
    }

    @PostMapping("/exam-schedules/{scheduleId}/results/submit")
    @PreAuthorize("hasPermission(null, 'RESULT_SUBMIT_FOR_APPROVAL')")
    public ScheduleResultResponse submit(@PathVariable UUID scheduleId) {
        return resultService.submit(scheduleId);
    }

    @PostMapping("/exam-schedules/{scheduleId}/results/decision")
    @PreAuthorize("hasPermission(null, 'RESULT_APPROVE')")
    public ScheduleResultResponse decide(@PathVariable UUID scheduleId, @Valid @RequestBody ResultDecisionRequest request) {
        return resultService.decide(scheduleId, request);
    }

    @PostMapping("/exam-schedules/{scheduleId}/results/publish")
    @PreAuthorize("hasPermission(null, 'RESULT_PUBLISH')")
    public ScheduleResultResponse publish(@PathVariable UUID scheduleId) {
        return resultService.publish(scheduleId);
    }

    @GetMapping("/me/results")
    @PreAuthorize("hasPermission(null, 'RESULT_VIEW_OWN')")
    public List<StudentResultResponse> mine() {
        return resultService.mine();
    }
}
