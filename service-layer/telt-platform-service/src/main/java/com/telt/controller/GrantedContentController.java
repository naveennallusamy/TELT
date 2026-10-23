package com.telt.controller;

import com.telt.dto.common.PageResponse;
import com.telt.dto.platform.CloneRequest;
import com.telt.dto.platform.GrantResponse;
import com.telt.dto.platform.GrantedQuestionResponse;
import com.telt.dto.question.QuestionResponse;
import com.telt.service.GrantedContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Platform banks granted to the caller's tenant (read-only).
 */
@RestController
@RequestMapping("/granted-banks")
@RequiredArgsConstructor
public class GrantedContentController {

    private final GrantedContentService grantedContentService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'QUESTION_VIEW')")
    public List<GrantResponse> grants() {
        return grantedContentService.grants();
    }

    @GetMapping("/{grantId}/questions")
    @PreAuthorize("hasPermission(null, 'QUESTION_VIEW')")
    public PageResponse<GrantedQuestionResponse> questions(@PathVariable UUID grantId,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return grantedContentService.questions(grantId, page, size);
    }

    @GetMapping("/{grantId}/questions/{questionId}")
    @PreAuthorize("hasPermission(null, 'QUESTION_VIEW')")
    public GrantedQuestionResponse question(@PathVariable UUID grantId, @PathVariable UUID questionId) {
        return grantedContentService.question(grantId, questionId);
    }

    @PostMapping("/{grantId}/questions/{questionId}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission(null, 'QUESTION_WRITE')")
    public QuestionResponse cloneQuestion(@PathVariable UUID grantId, @PathVariable UUID questionId,
                                          @Valid @RequestBody CloneRequest request) {
        return grantedContentService.cloneQuestion(grantId, questionId, request);
    }
}
