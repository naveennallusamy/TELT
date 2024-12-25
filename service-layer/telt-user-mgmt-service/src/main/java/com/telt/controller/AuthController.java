package com.telt.controller;

import com.telt.entity.*;
import com.telt.repository.UserRepository;
import com.telt.service.PasswordService;
import com.telt.util.JwtUtil;
import com.telt.util.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final PasswordService passwordService;

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    @Autowired
    PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, JwtUtil jwtUtil, PasswordService passwordService) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordService = passwordService;
    }

    @Operation(summary = "Authenticate user and generate JWT")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Successfully authenticated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class))), @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content)})
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            User user = loginRequest.getUsername().contains("@") ?
                    userRepository.findByEmail(loginRequest.getUsername()).orElseThrow(() -> new IllegalArgumentException("Invalid Username or Password")) :
                    userRepository.findByMobileNumber(loginRequest.getUsername()).orElseThrow(() -> new IllegalArgumentException("Invalid Username or Password"));

            if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
                throw new IllegalArgumentException("Invalid Password");
            }
            String token = jwtUtil.generateToken(user.getEmail(), user.getRole().getName(), TenantContext.getCurrentTenant());

            return ResponseEntity.ok(new AuthResponse(token));
        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        try {
            passwordService.forgotPassword(request.getEmailOrMobile());
            return ResponseEntity.ok("Password reset link has been sent to your email");
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestParam("token") String token, @RequestParam("newPassword") String newPassword) {
        try {
            passwordService.resetPassword(token, newPassword);
            return ResponseEntity.ok("Password reset successfully");
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(@RequestBody ChangePasswordRequest request) {
        try {
            passwordService.changePassword(request.getUserId(), request.getOldPassword(), request.getNewPassword());
            return ResponseEntity.ok("Password changed successfully");
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }
}

