package com.telt.entity.auth;

import lombok.Data;

@Data
public class ForgotPasswordRequest {
    private String emailOrMobile;
}
