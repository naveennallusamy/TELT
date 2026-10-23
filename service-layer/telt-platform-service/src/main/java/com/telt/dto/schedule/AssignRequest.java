package com.telt.dto.schedule;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Students by user id and/or departments; a department includes its sub-departments.
 */
public record AssignRequest(@Size(max = 1000) List<@NotNull UUID> userIds,
                            @Size(max = 100) List<@NotNull UUID> departmentIds) {
}
