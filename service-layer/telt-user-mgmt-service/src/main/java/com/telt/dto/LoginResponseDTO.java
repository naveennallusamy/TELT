package com.telt.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class LoginResponseDTO {
    private String userName;
    private String avatar;
    private String email;
    private List<String> authority = new ArrayList<>(Collections.singleton("TENANT_ADMIN"));
}
