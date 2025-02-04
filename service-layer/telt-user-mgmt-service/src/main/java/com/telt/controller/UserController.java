package com.telt.controller;

import com.telt.dto.UserDTO;
import com.telt.entity.user.User;
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

    /**
     * Registers a new user with the provided user details.
     *
     * @param userDTO Data transfer object containing user information, including personal details and addresses.
     * @return ResponseEntity containing the registered User object with HTTP status CREATED.
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody @Valid UserDTO userDTO) {
        User user =
                userService.registerUser(
                        userDTO.getUser(),
                        userDTO.getPermanentAddress(),
                        userDTO.getCurrentAddress());
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    /**
     * Retrieves a list of all users.
     *
     * @return ResponseEntity containing a list of User objects with HTTP status OK.
     */
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.status(HttpStatus.OK).body(userService.findAll());
    }
}
