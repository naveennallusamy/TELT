package com.telt.dto.access;

import com.telt.constants.ResourceType;
import com.telt.constants.ScopeType;
import com.telt.entity.role.RoleAssignment;

import java.time.Instant;
import java.util.UUID;

public record RoleAssignmentResponse(UUID assignmentId, String role, ScopeType scopeType, UUID departmentId,
                                     ResourceType resourceType, UUID resourceId, String grantedBy, Instant grantedAt) {

    public static RoleAssignmentResponse from(RoleAssignment a) {
        return new RoleAssignmentResponse(a.getAssignmentId(), a.getRole().getName(), a.getScopeType(),
                a.getDepartmentId(), a.getResourceType(), a.getResourceId(), a.getGrantedBy(), a.getGrantedAt());
    }
}
