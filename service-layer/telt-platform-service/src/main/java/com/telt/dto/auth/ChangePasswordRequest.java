package com.telt.dto.auth;

import com.telt.dto.common.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(@NotBlank String oldPassword, @NotBlank @StrongPassword String newPassword) {
}
