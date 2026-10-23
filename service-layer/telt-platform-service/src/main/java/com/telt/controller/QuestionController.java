package com.telt.controller;

import com.telt.constants.Difficulty;
import com.telt.constants.QuestionType;
import com.telt.constants.QuestionVersionStatus;
import com.telt.dto.common.PageResponse;
import com.telt.dto.question.*;
import com.telt.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    @PostMapping("/question-banks/{bankId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission(null, 'QUESTION_WRITE')")
    public QuestionResponse create(@PathVariable UUID bankId, @Valid @RequestBody CreateQuestionRequest request) {
        return questionService.create(bankId, request);
    }

    @GetMapping("/questions")
    @PreAuthorize("hasPermission(null, 'QUESTION_VIEW')")
    public PageResponse<QuestionSummary> search(@RequestParam(required = false) UUID bankId,
                                                @RequestParam(required = false) UUID categoryId,
                                                @RequestParam(required = false) QuestionType type,
                                                @RequestParam(required = false) QuestionVersionStatus status,
                                                @RequestParam(required = false) Difficulty difficulty,
                                                @RequestParam(required = false) String tag,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return questionService.search(new QuestionSearch(bankId, categoryId, type, status, difficulty, tag, q, page, size));
    }

    @GetMapping("/questions/{questionId}")
    @PreAuthorize("hasPermission(null, 'QUESTION_VIEW')")
    public QuestionResponse get(@PathVariable UUID questionId) {
        return questionService.get(questionId);
    }

    @PostMapping("/questions/{questionId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission(null, 'QUESTION_WRITE')")
    public QuestionVersionResponse newVersion(@PathVariable UUID questionId,
                                              @Valid @RequestBody QuestionVersionRequest request) {
        return questionService.newDraft(questionId, request);
    }

    @PutMapping("/questions/{questionId}/versions/{versionNo}")
    @PreAuthorize("hasPermission(null, 'QUESTION_WRITE')")
    public QuestionVersionResponse updateDraft(@PathVariable UUID questionId, @PathVariable int versionNo,
                                               @Valid @RequestBody QuestionVersionRequest request) {
        return questionService.updateDraft(questionId, versionNo, request);
    }

    @PostMapping("/questions/{questionId}/versions/{versionNo}/submit")
    @PreAuthorize("hasPermission(null, 'QUESTION_WRITE')")
    public QuestionVersionResponse submit(@PathVariable UUID questionId, @PathVariable int versionNo) {
        return questionService.submit(questionId, versionNo);
    }

    @PostMapping("/questions/{questionId}/versions/{versionNo}/review")
    @PreAuthorize("hasPermission(null, 'QUESTION_REVIEW')")
    public QuestionVersionResponse review(@PathVariable UUID questionId, @PathVariable int versionNo,
                                          @Valid @RequestBody ReviewRequest request) {
        return questionService.review(questionId, versionNo, request);
    }

    @PostMapping("/questions/{questionId}/retire")
    @PreAuthorize("hasPermission(null, 'QUESTION_PUBLISH')")
    public QuestionResponse retire(@PathVariable UUID questionId) {
        return questionService.retire(questionId);
    }
}
