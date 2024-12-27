package com.telt.service;

import com.telt.entity.User;

import java.util.List;

public interface UserService {

    User registerUser(User user);

    List<User> findAll();
}
