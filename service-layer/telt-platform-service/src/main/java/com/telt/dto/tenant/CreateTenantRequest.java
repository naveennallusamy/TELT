package com.telt.dto.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(@NotBlank @Size(max = 200) String tenantName, @Size(max = 1000) String description) {
}
