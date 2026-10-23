package com.telt.dto.common;

import com.telt.entity.address.AddressInfo;
import jakarta.validation.constraints.Size;

public record AddressDto(
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 100) String country,
        @Size(max = 20) String zipCode) {

    public AddressInfo toEntity() {
        AddressInfo address = new AddressInfo();
        address.setAddressLine1(addressLine1);
        address.setAddressLine2(addressLine2);
        address.setCity(city);
        address.setState(state);
        address.setCountry(country);
        address.setZipCode(zipCode);
        return address;
    }

    public static AddressDto from(AddressInfo address) {
        return address == null ? null : new AddressDto(address.getAddressLine1(), address.getAddressLine2(),
                address.getCity(), address.getState(), address.getCountry(), address.getZipCode());
    }
}
