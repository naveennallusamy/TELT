package com.telt.controller;

import com.telt.entity.auth.AuthResponse;
import com.telt.entity.auth.ChangePasswordRequest;
import com.telt.entity.auth.ForgotPasswordRequest;
import com.telt.entity.auth.LoginRequest;
import com.telt.service.AuthService;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@CrossOrigin
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * Authenticates user and generates a JWT token.
     *
     * @param loginRequest User credentials
     * @return ResponseEntity containing the generated JWT token
     * @throws BadCredentialsException if the credentials are invalid
     */
    @Operation(summary = "Authenticate user and generate JWT")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Successfully authenticated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class))), @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content)})
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequest loginRequest) {
        try {
            return ResponseEntity.status(HttpStatus.OK).body(authService.login(loginRequest));
        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
        }
    }

    /**
     * Initiates the forgot password process by generating a password reset token
     * and sending a reset link to the user's email or mobile number.
     *
     * @param request Contains the email or mobile number of the user requesting password reset.
     * @return ResponseEntity containing a message indicating that the password reset
     * link has been sent, with an HTTP status of OK, or an error message with a BAD_REQUEST status.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        try {
            authService.forgotPassword(request.getEmailOrMobile());
            return ResponseEntity.status(HttpStatus.OK).body("Password reset link has been sent to your email");
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    /**
     * Resets the user's password using a provided reset token and a new password.
     * <p>
     * This endpoint expects a valid and non-expired token along with the new password
     * to set for the user. If the token is invalid or expired, an error response is returned.
     *
     * @param token       The password reset token
     * @param newPassword The new password for the user
     * @return ResponseEntity containing a success message with HTTP status OK,
     * or an error message with HTTP status BAD_REQUEST if the reset fails.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestParam("token") String token, @RequestParam("newPassword") String newPassword) {
        try {
            authService.resetPassword(token, newPassword);
            return ResponseEntity.status(HttpStatus.OK).body("Password reset successfully");
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    /**
     * Changes the password for a user.
     * <p>
     * This endpoint expects a valid user ID, old password, and new password
     * to set for the user. If the user is not found or the old password does not
     * match, an error response is returned.
     *
     * @param request Contains the user ID, old password, and new password.
     * @return ResponseEntity containing a success message with HTTP status CREATED,
     * or an error message with HTTP status BAD_REQUEST if the reset fails.
     */
    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(@RequestBody ChangePasswordRequest request) {
        try {
            authService.changePassword(request.getUserId(), request.getOldPassword(), request.getNewPassword());
            return ResponseEntity.status(HttpStatus.CREATED).body("Password changed successfully");
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }
}

