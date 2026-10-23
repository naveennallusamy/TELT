package com.telt.controller;

import com.telt.dto.schedule.AssignRequest;
import com.telt.dto.schedule.AssignmentResponse;
import com.telt.dto.schedule.ScheduleRequest;
import com.telt.dto.schedule.ScheduleResponse;
import com.telt.service.ExamScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/exam-schedules")
@RequiredArgsConstructor
public class ExamScheduleController {

    private final ExamScheduleService scheduleService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'EXAM_VIEW') or hasPermission(null, 'EXAM_ASSIGN')")
    public List<ScheduleResponse> list(@RequestParam(required = false) UUID examId) {
        return scheduleService.list(examId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission(null, 'EXAM_ASSIGN')")
    public ScheduleResponse create(@Valid @RequestBody ScheduleRequest request) {
        return scheduleService.create(request);
    }

    @GetMapping("/{scheduleId}")
    @PreAuthorize("hasPermission(null, 'EXAM_VIEW') or hasPermission(null, 'EXAM_ASSIGN')")
    public ScheduleResponse get(@PathVariable UUID scheduleId) {
        return scheduleService.get(scheduleId);
    }

    @PutMapping("/{scheduleId}")
    @PreAuthorize("hasPermission(null, 'EXAM_ASSIGN')")
    public ScheduleResponse update(@PathVariable UUID scheduleId, @Valid @RequestBody ScheduleRequest request) {
        return scheduleService.update(scheduleId, request);
    }

    @PostMapping("/{scheduleId}/cancel")
    @PreAuthorize("hasPermission(null, 'EXAM_ASSIGN')")
    public ScheduleResponse cancel(@PathVariable UUID scheduleId) {
        return scheduleService.cancel(scheduleId);
    }

    @GetMapping("/{scheduleId}/assignments")
    @PreAuthorize("hasPermission(null, 'EXAM_VIEW') or hasPermission(null, 'EXAM_ASSIGN')")
    public List<AssignmentResponse> assignments(@PathVariable UUID scheduleId) {
        return scheduleService.assignments(scheduleId);
    }

    @PostMapping("/{scheduleId}/assignments")
    @PreAuthorize("hasPermission(null, 'EXAM_ASSIGN')")
    public List<AssignmentResponse> assign(@PathVariable UUID scheduleId, @Valid @RequestBody AssignRequest request) {
        return scheduleService.assign(scheduleId, request);
    }

    @DeleteMapping("/{scheduleId}/assignments/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasPermission(null, 'EXAM_ASSIGN')")
    public void unassign(@PathVariable UUID scheduleId, @PathVariable UUID assignmentId) {
        scheduleService.unassign(scheduleId, assignmentId);
    }
}
