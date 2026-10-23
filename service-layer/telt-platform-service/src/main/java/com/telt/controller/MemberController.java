package com.telt.controller;

import com.telt.dto.access.*;
import com.telt.service.MembershipService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Members of the active tenant: access mode, department and scoped role assignments.
 */
@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {

    private final MembershipService membershipService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'USER_VIEW')")
    public List<MemberResponse> list() {
        return membershipService.listMembers();
    }

    @Operation(summary = "Add an existing staff user to this tenant")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('TENANT', 'USER_MANAGE')")
    public MemberResponse add(@Valid @RequestBody AddMemberRequest request) {
        return membershipService.addExisting(request);
    }

    @Operation(summary = "Change a member's department or a sub-admin's access mode (tenant-wide / department-scoped)")
    @PatchMapping("/{userId}")
    @PreAuthorize("hasPermission(null, 'USER_MANAGE')")
    public MemberResponse update(@PathVariable UUID userId, @Valid @RequestBody UpdateMemberRequest request) {
        return membershipService.update(userId, request);
    }

    @GetMapping("/{userId}/role-assignments")
    @PreAuthorize("hasPermission(null, 'USER_VIEW')")
    public List<RoleAssignmentResponse> assignments(@PathVariable UUID userId) {
        return membershipService.assignments(userId);
    }

    @PostMapping("/{userId}/role-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission(null, 'ROLE_ASSIGN')")
    public RoleAssignmentResponse assign(@PathVariable UUID userId, @Valid @RequestBody AssignRoleRequest request) {
        return membershipService.assign(userId, request);
    }

    @DeleteMapping("/{userId}/role-assignments/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasPermission(null, 'ROLE_ASSIGN')")
    public void revoke(@PathVariable UUID userId, @PathVariable UUID assignmentId) {
        membershipService.revoke(userId, assignmentId);
    }
}
