package com.telt.entity.auth;

import com.telt.dto.LoginResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private String token;
    private LoginResponseDTO user;
}
