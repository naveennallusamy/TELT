package com.telt.dto.user;

import com.telt.constants.AccessMode;
import com.telt.constants.AccountType;
import com.telt.dto.common.AddressDto;
import com.telt.dto.common.StrongPassword;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.UUID;

/**
 * Admin-initiated user creation. Non-super-admin accounts get a membership in the target tenant (the caller's own
 * tenant for tenant staff; {@code tenantName} for super admins). If {@code password} is omitted a random one is set
 * and a password-setup link is e-mailed. Either way the user must change the password on first login.
 */
public record RegisterUserRequest(
        @Size(max = 100) String username,
        @NotBlank @Email @Size(max = 320) String email,
        @NotNull @Positive Long mobileNumber,
        @Size(max = 10) String countryCode,
        @StrongPassword String password,
        @NotNull AccountType role,
        @Size(max = 200) String tenantName,
        AccessMode accessMode,
        UUID departmentId,
        @Valid PersonalInfoDto personalInfo,
        @Valid AddressDto currentAddress,
        @Valid AddressDto permanentAddress) {
}
