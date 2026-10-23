package com.telt.dto.auth;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SwitchTenantRequest(@NotNull UUID tenantId) {
}
