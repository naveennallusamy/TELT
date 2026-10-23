package com.telt.controller;

import com.telt.dto.exam.*;
import com.telt.dto.question.ReviewRequest;
import com.telt.service.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'EXAM_VIEW')")
    public List<ExamResponse> list() {
        return examService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission(null, 'EXAM_CREATE')")
    public ExamDetailResponse create(@Valid @RequestBody CreateExamRequest request) {
        return examService.create(request);
    }

    @GetMapping("/{examId}")
    @PreAuthorize("hasPermission(null, 'EXAM_VIEW')")
    public ExamDetailResponse get(@PathVariable UUID examId) {
        return examService.get(examId);
    }

    @PostMapping("/{examId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission(null, 'EXAM_EDIT')")
    public ExamVersionResponse newVersion(@PathVariable UUID examId, @Valid @RequestBody ExamVersionRequest request) {
        return examService.newDraft(examId, request);
    }

    @PutMapping("/{examId}/versions/{versionNo}")
    @PreAuthorize("hasPermission(null, 'EXAM_EDIT')")
    public ExamVersionResponse updateDraft(@PathVariable UUID examId, @PathVariable int versionNo,
                                           @Valid @RequestBody ExamVersionRequest request) {
        return examService.updateDraft(examId, versionNo, request);
    }

    @PostMapping("/{examId}/versions/{versionNo}/submit")
    @PreAuthorize("hasPermission(null, 'EXAM_EDIT')")
    public ExamVersionResponse submit(@PathVariable UUID examId, @PathVariable int versionNo) {
        return examService.submit(examId, versionNo);
    }

    @PostMapping("/{examId}/versions/{versionNo}/review")
    @PreAuthorize("hasPermission(null, 'EXAM_REVIEW')")
    public ExamVersionResponse review(@PathVariable UUID examId, @PathVariable int versionNo,
                                      @Valid @RequestBody ReviewRequest request) {
        return examService.review(examId, versionNo, request);
    }

    @PostMapping("/{examId}/versions/{versionNo}/publish")
    @PreAuthorize("hasPermission(null, 'EXAM_PUBLISH')")
    public ExamVersionResponse publish(@PathVariable UUID examId, @PathVariable int versionNo) {
        return examService.publish(examId, versionNo);
    }

    @GetMapping("/{examId}/versions/{versionNo}/questions")
    @PreAuthorize("hasPermission(null, 'EXAM_VIEW')")
    public List<SnapshotResponse> snapshot(@PathVariable UUID examId, @PathVariable int versionNo) {
        return examService.snapshot(examId, versionNo);
    }

    @PostMapping("/{examId}/archive")
    @PreAuthorize("hasPermission(null, 'EXAM_PUBLISH')")
    public ExamResponse archive(@PathVariable UUID examId) {
        return examService.archive(examId);
    }
}
