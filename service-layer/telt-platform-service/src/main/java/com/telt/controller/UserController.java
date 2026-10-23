package com.telt.controller;

import com.telt.constants.UserStatus;
import com.telt.dto.common.MessageResponse;
import com.telt.dto.user.RegisterUserRequest;
import com.telt.dto.user.UserResponse;
import com.telt.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * User administration. Tenant admins only see and manage users of their own tenant.
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'TENANT_ADMIN')")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Create a user (must change password on first login)")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse registerUser(@Valid @RequestBody RegisterUserRequest request) {
        return userService.register(request);
    }

    @GetMapping
    public List<UserResponse> getAllUsers() {
        return userService.findAll();
    }

    @GetMapping("/{userId}")
    public UserResponse getUser(@PathVariable UUID userId) {
        return userService.findById(userId);
    }

    @Operation(summary = "Activate a user account")
    @PostMapping("/{userId}/activate")
    public UserResponse activate(@PathVariable UUID userId) {
        return userService.setStatus(userId, UserStatus.ACTIVE);
    }

    @Operation(summary = "Deactivate a user account and revoke its sessions")
    @PostMapping("/{userId}/deactivate")
    public UserResponse deactivate(@PathVariable UUID userId) {
        return userService.setStatus(userId, UserStatus.INACTIVE);
    }

    @Operation(summary = "Clear a lockout caused by failed logins")
    @PostMapping("/{userId}/unlock")
    public UserResponse unlock(@PathVariable UUID userId) {
        return userService.unlock(userId);
    }

    @Operation(summary = "E-mail the user a password reset link")
    @PostMapping("/{userId}/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse sendPasswordReset(@PathVariable UUID userId) {
        userService.sendPasswordReset(userId);
        return new MessageResponse("Password reset link sent");
    }
}
