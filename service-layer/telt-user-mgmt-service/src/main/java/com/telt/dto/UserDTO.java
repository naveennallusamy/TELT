package com.telt.dto;

import com.telt.entity.AddressInfo;
import com.telt.entity.User;
import lombok.Data;

@Data
public class UserDTO {
    private User user;
    private AddressInfo currentAddress;
    private AddressInfo permanentAddress;
}