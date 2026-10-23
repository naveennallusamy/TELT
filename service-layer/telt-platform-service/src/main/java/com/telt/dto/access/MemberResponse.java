package com.telt.dto.access;

import com.telt.constants.AccessMode;
import com.telt.constants.MembershipType;
import com.telt.constants.RecordStatus;

import java.util.List;
import java.util.UUID;

public record MemberResponse(UUID userId, String email, String username, MembershipType membershipType,
                             AccessMode accessMode, UUID departmentId, RecordStatus status,
                             List<RoleAssignmentResponse> roleAssignments) {
}
