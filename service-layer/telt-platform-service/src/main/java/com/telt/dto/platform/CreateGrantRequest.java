package com.telt.dto.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateGrantRequest(@NotNull UUID bankId, @NotNull UUID tenantId, @NotNull @Valid GrantSettings settings) {
}
