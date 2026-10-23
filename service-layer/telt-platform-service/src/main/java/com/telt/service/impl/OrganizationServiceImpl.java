package com.telt.service.impl;

import com.telt.dto.organization.OrganizationResponse;
import com.telt.dto.organization.RegisterOrganizationRequest;
import com.telt.entity.address.AddressInfo;
import com.telt.entity.organization.OrgAddressAssoc;
import com.telt.entity.organization.OrgAddressId;
import com.telt.entity.organization.OrganizationDetails;
import com.telt.repository.OrganizationRepository;
import com.telt.service.AddressService;
import com.telt.service.OrganizationService;
import com.telt.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final TenantService tenantService;
    private final AddressService addressService;

    @Override
    @Transactional
    public OrganizationResponse register(RegisterOrganizationRequest request) {
        OrganizationDetails org = new OrganizationDetails();
        org.setOrganizationName(request.organizationName());
        org.setWebsite(request.website());
        org.setOfficePhone(request.officePhone());
        org.setTenant(tenantService.resolveTargetTenant(request.tenantName(), false));
        if (request.address() != null) {
            AddressInfo address = addressService.findOrCreateAddress(request.address().toEntity());
            OrgAddressId id = new OrgAddressId();
            id.setAddressType("organization");
            OrgAddressAssoc assoc = new OrgAddressAssoc();
            assoc.setOrgAddressId(id);
            assoc.setOrganizationDetails(org);
            assoc.setAddress(address);
            org.setOrgAddress(assoc);
        }
        return OrganizationResponse.from(organizationRepository.save(org));
    }
}
