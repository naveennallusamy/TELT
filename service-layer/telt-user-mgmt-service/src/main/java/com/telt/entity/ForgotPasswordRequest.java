package com.telt.entity;

import lombok.Data;

@Data
public class ForgotPasswordRequest {
    private String emailOrMobile;
}
