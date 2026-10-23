package com.telt.service;

import com.telt.dto.organization.OrganizationResponse;
import com.telt.dto.organization.RegisterOrganizationRequest;

public interface OrganizationService {

    OrganizationResponse register(RegisterOrganizationRequest request);
}
