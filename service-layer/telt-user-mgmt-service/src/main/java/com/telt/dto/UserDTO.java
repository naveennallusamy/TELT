package com.telt.dto;

import com.telt.entity.address.AddressInfo;
import com.telt.entity.user.User;
import lombok.Data;

@Data
public class UserDTO {
    private User user;
    private AddressInfo currentAddress;
    private AddressInfo permanentAddress;
}