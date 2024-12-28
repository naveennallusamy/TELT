package com.telt.controller;

import com.telt.dto.UserDTO;
import com.telt.entity.User;
import com.telt.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    // Register User API
    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody @Valid UserDTO userDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                userService.registerUser(
                        userDTO.getUser(),
                        userDTO.getPermanentAddress(),
                        userDTO.getCurrentAddress()));
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.status(HttpStatus.OK).body(userService.findAll());
    }
}
