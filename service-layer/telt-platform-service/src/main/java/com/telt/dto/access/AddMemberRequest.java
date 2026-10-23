package com.telt.dto.access;

import com.telt.constants.AccessMode;
import com.telt.constants.MembershipType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Adds an existing (staff) user to the current tenant. New users are created with {@code POST /user/register}.
 */
public record AddMemberRequest(@NotBlank @Email String email, @NotNull MembershipType membershipType,
                               AccessMode accessMode, UUID departmentId) {
}
