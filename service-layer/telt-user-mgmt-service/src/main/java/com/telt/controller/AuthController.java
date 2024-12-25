package com.telt.controller;

import com.telt.entity.AuthResponse;
import com.telt.entity.ChangePasswordRequest;
import com.telt.entity.ForgotPasswordRequest;
import com.telt.entity.LoginRequest;
import com.telt.repository.UserRepository;
import com.telt.service.AuthService;
import com.telt.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    @Autowired
    PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, JwtUtil jwtUtil, AuthService authService) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.authService = authService;
    }

    @Operation(summary = "Authenticate user and generate JWT")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Successfully authenticated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class))), @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content)})
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequest loginRequest) {
        try {
            return ResponseEntity.ok(authService.login(loginRequest));
        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        try {
            authService.forgotPassword(request.getEmailOrMobile());
            return ResponseEntity.ok("Password reset link has been sent to your email");
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestParam("token") String token, @RequestParam("newPassword") String newPassword) {
        try {
            authService.resetPassword(token, newPassword);
            return ResponseEntity.ok("Password reset successfully");
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(@RequestBody ChangePasswordRequest request) {
        try {
            authService.changePassword(request.getUserId(), request.getOldPassword(), request.getNewPassword());
            return ResponseEntity.ok("Password changed successfully");
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }
}

