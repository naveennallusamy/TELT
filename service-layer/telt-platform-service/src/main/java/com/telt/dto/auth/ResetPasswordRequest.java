package com.telt.dto.auth;

import com.telt.dto.common.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(@NotBlank String token, @NotBlank @StrongPassword String newPassword) {
}
