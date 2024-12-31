package com.telt.entity.auth;

import lombok.Data;

@Data
public class ChangePasswordRequest {

    private String userId;  // The user's ID, to identify the user
    private String oldPassword;  // The current password
    private String newPassword;  // The new password
}

