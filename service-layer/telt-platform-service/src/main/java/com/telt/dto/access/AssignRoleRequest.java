package com.telt.dto.access;

import com.telt.constants.ScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignRoleRequest(@NotBlank String role, @NotNull ScopeType scopeType, UUID departmentId) {
}
