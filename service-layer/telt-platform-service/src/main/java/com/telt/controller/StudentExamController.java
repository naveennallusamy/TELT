package com.telt.controller;

import com.telt.dto.schedule.StudentExamResponse;
import com.telt.service.ExamScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class StudentExamController {

    private final ExamScheduleService scheduleService;

    @GetMapping("/me/exams")
    @PreAuthorize("hasPermission(null, 'ATTEMPT_OWN')")
    public List<StudentExamResponse> myExams() {
        return scheduleService.myExams();
    }
}
