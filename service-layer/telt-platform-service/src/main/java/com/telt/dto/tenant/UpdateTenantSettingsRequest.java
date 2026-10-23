package com.telt.dto.tenant;

import com.telt.constants.AccessMode;
import jakarta.validation.constraints.NotNull;

public record UpdateTenantSettingsRequest(@NotNull AccessMode subAdminDefaultAccessMode,
                                          @NotNull Boolean separationOfDuties) {
}
