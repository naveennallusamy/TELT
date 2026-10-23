package com.telt.dto.user;

import com.telt.constants.AccessMode;
import com.telt.constants.MembershipType;
import com.telt.constants.RecordStatus;

import java.util.UUID;

public record MembershipSummary(UUID tenantId, String tenantName, MembershipType membershipType, AccessMode accessMode,
                                UUID departmentId, RecordStatus status) {
}
