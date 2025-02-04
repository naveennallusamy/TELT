package com.telt.entity.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class LoginRequest {

    @Nonnull
    @NotEmpty
    @Schema(description = "User email or mobile number", example = "user@example.com")
    private String userName;
    @Nonnull
    @NotEmpty
    @Schema(description = "User password", example = "password123")
    private String password;  // Password
}
