package com.telt.dto;

import com.telt.entity.address.AddressInfo;
import com.telt.entity.organization.OrganizationDetails;
import lombok.Data;

@Data
public class OrganizationRegisterDTO {
    private OrganizationDetails organizationDetails;
    private AddressInfo addressInfo;
}
