package com.telt.dto.user;

import com.telt.constants.UserStatus;
import com.telt.dto.common.AddressDto;
import com.telt.entity.user.User;
import com.telt.entity.user.UserAddressAssoc;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        Long mobileNumber,
        String countryCode,
        String role,
        UUID tenantId,
        String tenantName,
        UserStatus status,
        boolean locked,
        boolean passwordChangeRequired,
        Instant lastLoginAt,
        PersonalInfoDto personalInfo,
        AddressDto currentAddress,
        AddressDto permanentAddress) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getMobileNumber(),
                user.getCountryCode(), user.getRole().getName(),
                user.getTenant() == null ? null : user.getTenant().getTenantId(),
                user.getTenant() == null ? null : user.getTenant().getTenantName(),
                user.getStatus(), user.isLocked(Instant.now()), user.isPasswordChangeRequired(),
                user.getLastLoginAt(), PersonalInfoDto.from(user.getPersonalInfo()),
                address(user, "current"), address(user, "permanent"));
    }

    private static AddressDto address(User user, String type) {
        return user.getUserAddressDetails().stream()
                .filter(a -> type.equals(a.getUserAddressId().getAddressType()))
                .map(UserAddressAssoc::getAddress)
                .map(AddressDto::from)
                .findFirst()
                .orElse(null);
    }
}
