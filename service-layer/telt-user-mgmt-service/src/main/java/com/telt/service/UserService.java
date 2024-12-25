package com.telt.service;

import java.util.Optional;
import com.telt.entity.User;

public interface UserService {

    User registerUser(String email, String mobile, String password, String roleName,
                      Optional<String> tenantName);

    User changePassword(String identifier, String newPassword);

    String login(String emailOrMobile, String password);
}
