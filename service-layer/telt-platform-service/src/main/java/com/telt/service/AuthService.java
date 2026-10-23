package com.telt.service;

import com.telt.dto.auth.AuthResponse;
import com.telt.dto.auth.LoginRequest;
import com.telt.entity.user.User;

import java.util.UUID;

public interface AuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(String refreshToken);

    void logout(String refreshToken);

    void logoutAll(UUID userId);

    void forgotPassword(String emailOrMobile);

    void resetPassword(String token, String newPassword);

    AuthResponse changePassword(UUID userId, String oldPassword, String newPassword);

    void sendPasswordResetLink(User user);
}
