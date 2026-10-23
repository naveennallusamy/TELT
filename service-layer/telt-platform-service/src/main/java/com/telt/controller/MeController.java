package com.telt.controller;

import com.telt.dto.user.UpdateProfileRequest;
import com.telt.dto.user.UserResponse;
import com.telt.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MeController {

    private final UserService userService;

    @Operation(summary = "Current user's profile")
    @GetMapping
    public UserResponse me() {
        return userService.currentUser();
    }

    @Operation(summary = "Update the current user's profile")
    @PutMapping
    public UserResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateCurrentUser(request);
    }
}
