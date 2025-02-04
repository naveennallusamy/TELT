package com.telt.service;

import com.telt.entity.address.AddressInfo;
import com.telt.entity.user.User;

import java.util.List;

public interface UserService {

    User registerUser(User user, AddressInfo permanentAddress, AddressInfo currentAddress);

    List<User> findAll();
}
