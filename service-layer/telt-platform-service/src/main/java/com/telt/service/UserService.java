package com.telt.service;

import com.telt.constants.UserStatus;
import com.telt.dto.user.RegisterUserRequest;
import com.telt.dto.user.UpdateProfileRequest;
import com.telt.dto.user.UserResponse;

import java.util.List;
import java.util.UUID;

public interface UserService {

    UserResponse register(RegisterUserRequest request);

    List<UserResponse> findAll();

    UserResponse findById(UUID userId);

    UserResponse currentUser();

    UserResponse updateCurrentUser(UpdateProfileRequest request);

    UserResponse setStatus(UUID userId, UserStatus status);

    UserResponse unlock(UUID userId);

    void sendPasswordReset(UUID userId);
}
