package com.telt.dto;

import com.telt.entity.address.AddressInfo;

public class UserResponseDto {
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private AddressInfo currentAddress;
    private AddressInfo permanentAddress;
}
