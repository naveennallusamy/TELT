package com.telt.dto.organization;

import com.telt.dto.common.AddressDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RegisterOrganizationRequest(
        @NotBlank @Size(max = 200) String organizationName,
        @Size(max = 255) String website,
        @Positive Long officePhone,
        @Size(max = 200) String tenantName,
        @Valid AddressDto address) {
}
