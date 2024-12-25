package com.telt.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class LoginRequest {
    @Schema(description = "User email or mobile number", example = "user@example.com")
    private String username;
    @Schema(description = "User password", example = "password123")
    private String password;  // Password
}
