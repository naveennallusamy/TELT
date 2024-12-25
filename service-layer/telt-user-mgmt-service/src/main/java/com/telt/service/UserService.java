package com.telt.service;

import com.telt.entity.User;

import java.util.Optional;

public interface UserService {

    User registerUser(String email, Long mobile, String password, String roleName,
                      Optional<String> tenantName);
}
