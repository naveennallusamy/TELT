package com.telt.service;

import com.telt.entity.AuthResponse;
import com.telt.entity.LoginRequest;

public interface AuthService {

    AuthResponse login(LoginRequest loginRequest);

    void forgotPassword(String emailOrMobile) throws Exception;

    void resetPassword(String token, String newPassword) throws Exception;

    void changePassword(String userId, String oldPassword, String newPassword) throws Exception;

}
