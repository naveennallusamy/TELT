package com.telt.controller;

import com.telt.dto.question.*;
import com.telt.service.QuestionBankService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Department-level checks happen in the service against the bank's department.
 */
@RestController
@RequiredArgsConstructor
public class QuestionBankController {

    private final QuestionBankService bankService;

    @GetMapping("/question-banks")
    @PreAuthorize("hasPermission(null, 'QUESTION_VIEW')")
    public List<BankResponse> list() {
        return bankService.list();
    }

    @PostMapping("/question-banks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission(null, 'QBANK_CREATE')")
    public BankResponse create(@Valid @RequestBody CreateBankRequest request) {
        return bankService.create(request);
    }

    @PutMapping("/question-banks/{bankId}")
    @PreAuthorize("hasPermission(null, 'QBANK_CREATE')")
    public BankResponse update(@PathVariable UUID bankId, @Valid @RequestBody UpdateBankRequest request) {
        return bankService.update(bankId, request);
    }

    @GetMapping("/question-categories")
    @PreAuthorize("hasPermission(null, 'QUESTION_VIEW')")
    public List<CategoryResponse> categories() {
        return bankService.categories();
    }

    @PostMapping("/question-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission(null, 'QBANK_CREATE')")
    public CategoryResponse createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return bankService.createCategory(request);
    }
}
