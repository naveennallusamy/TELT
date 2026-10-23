package com.telt.dto.user;

import com.telt.dto.common.AddressDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Self-service profile update. E-mail and mobile number are login identifiers and are not changeable here.
 * Omitted (null) fields are left unchanged.
 */
public record UpdateProfileRequest(
        @Size(max = 100) String username,
        @Size(max = 10) String countryCode,
        @Valid PersonalInfoDto personalInfo,
        @Valid AddressDto currentAddress,
        @Valid AddressDto permanentAddress) {
}
