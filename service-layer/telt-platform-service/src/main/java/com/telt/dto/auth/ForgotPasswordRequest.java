package com.telt.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(@NotBlank String emailOrMobile) {
}
