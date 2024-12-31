package com.telt.service;

import com.telt.entity.address.AddressInfo;
import com.telt.entity.organization.OrganizationDetails;

public interface OrganizationService {

    OrganizationDetails register(OrganizationDetails organizationDetails, AddressInfo addressInfo);
}
