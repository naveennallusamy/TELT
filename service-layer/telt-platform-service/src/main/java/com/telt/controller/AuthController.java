package com.telt.controller;

import com.telt.dto.auth.*;
import com.telt.dto.common.MessageResponse;
import com.telt.service.AuthService;
import com.telt.util.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Authenticate with e-mail, mobile number or username")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Exchange a refresh token for a new access/refresh token pair (rotation)")
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @Operation(summary = "Revoke the session identified by the refresh token")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Revoke all sessions of the current user")
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll() {
        authService.logoutAll(TenantContext.requireCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Send a password reset link (always succeeds to avoid account enumeration)")
    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.emailOrMobile());
        return new MessageResponse("If the account exists, a password reset link has been sent");
    }

    @Operation(summary = "Set a new password using a reset link token")
    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return new MessageResponse("Password has been reset");
    }

    @Operation(summary = "Change the current user's password; other sessions are signed out")
    @PostMapping("/change-password")
    public AuthResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(TenantContext.requireCurrentUserId(), request.oldPassword(),
                request.newPassword());
    }
}
