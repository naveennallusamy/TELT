package com.telt.dto.platform;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CloneRequest(@NotNull UUID bankId, UUID categoryId) {
}
