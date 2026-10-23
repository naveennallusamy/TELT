package com.telt.dto.organization;

import com.telt.dto.common.AddressDto;
import com.telt.entity.organization.OrganizationDetails;

import java.util.UUID;

public record OrganizationResponse(UUID id, String organizationName, String website, Long officePhone,
                                   UUID tenantId, AddressDto address) {

    public static OrganizationResponse from(OrganizationDetails org) {
        return new OrganizationResponse(org.getId(), org.getOrganizationName(), org.getWebsite(),
                org.getOfficePhone(), org.getTenant() == null ? null : org.getTenant().getTenantId(),
                org.getOrgAddress() == null ? null : AddressDto.from(org.getOrgAddress().getAddress()));
    }
}
