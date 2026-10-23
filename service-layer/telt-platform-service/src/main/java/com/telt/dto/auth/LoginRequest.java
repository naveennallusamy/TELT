package com.telt.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Schema(description = "E-mail, mobile number or username", example = "user@example.com") String userName,
        @NotBlank String password) {
}
