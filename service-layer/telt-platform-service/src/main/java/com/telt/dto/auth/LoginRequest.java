package com.telt.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record LoginRequest(
        @NotBlank @Schema(description = "E-mail, mobile number or username", example = "user@example.com") String userName,
        @NotBlank String password,
        @Schema(description = "Tenant to sign in to when the user belongs to several (optional)") UUID tenantId) {
}
