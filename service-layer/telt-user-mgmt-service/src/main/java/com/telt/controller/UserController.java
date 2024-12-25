package com.telt.controller;

import com.telt.entity.User;
import com.telt.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/users")
public class UserController {
    @Autowired
    private UserService userService;

    // Register User API
    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestParam String email, @RequestParam String mobile, @RequestParam String password, @RequestParam String roleName, @RequestParam(required = false) String tenantName) {
        Optional<String> tenant = Optional.ofNullable(tenantName);
        return ResponseEntity.ok(userService.registerUser(email, mobile, password, roleName, tenant));
    }
}
