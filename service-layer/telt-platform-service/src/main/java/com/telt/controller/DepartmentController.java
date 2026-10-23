package com.telt.controller;

import com.telt.dto.department.CreateDepartmentRequest;
import com.telt.dto.department.DepartmentResponse;
import com.telt.dto.department.UpdateDepartmentRequest;
import com.telt.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    public List<DepartmentResponse> list() {
        return departmentService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('TENANT', 'DEPARTMENT_MANAGE')")
    public DepartmentResponse create(@Valid @RequestBody CreateDepartmentRequest request) {
        return departmentService.create(request);
    }

    @PutMapping("/{departmentId}")
    @PreAuthorize("hasPermission('TENANT', 'DEPARTMENT_MANAGE')")
    public DepartmentResponse update(@PathVariable UUID departmentId, @Valid @RequestBody UpdateDepartmentRequest request) {
        return departmentService.update(departmentId, request);
    }

    @DeleteMapping("/{departmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasPermission('TENANT', 'DEPARTMENT_MANAGE')")
    public void delete(@PathVariable UUID departmentId) {
        departmentService.delete(departmentId);
    }
}
