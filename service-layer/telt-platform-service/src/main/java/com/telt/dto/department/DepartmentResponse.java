package com.telt.dto.department;

import com.telt.constants.RecordStatus;
import com.telt.entity.tenant.Department;

import java.util.UUID;

public record DepartmentResponse(UUID departmentId, UUID parentDepartmentId, String code, String name,
                                 RecordStatus status) {

    public static DepartmentResponse from(Department d) {
        return new DepartmentResponse(d.getDepartmentId(), d.getParentDepartmentId(), d.getCode(), d.getName(),
                d.getStatus());
    }
}
