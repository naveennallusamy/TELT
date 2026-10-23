package com.telt.dto.department;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateDepartmentRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String code,
        @NotBlank @Size(max = 200) String name,
        UUID parentDepartmentId) {
}
