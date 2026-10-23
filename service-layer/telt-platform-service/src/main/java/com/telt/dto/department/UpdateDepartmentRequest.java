package com.telt.dto.department;

import com.telt.constants.RecordStatus;
import jakarta.validation.constraints.Size;

/**
 * Omitted (null) fields are left unchanged. Moving a department to another parent is not supported.
 */
public record UpdateDepartmentRequest(@Size(min = 1, max = 200) String name, RecordStatus status) {
}
