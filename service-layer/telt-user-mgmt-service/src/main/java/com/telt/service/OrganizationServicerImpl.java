package com.telt.service;

import com.telt.entity.address.AddressInfo;
import com.telt.entity.organization.OrgAddressAssoc;
import com.telt.entity.organization.OrgAddressId;
import com.telt.entity.organization.OrganizationDetails;
import com.telt.entity.tenant.Tenant;
import com.telt.repository.OrganizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrganizationServicerImpl implements OrganizationService {

    @Autowired
    OrganizationRepository organizationRepository;

    @Autowired
    TenantService tenantService;

    @Autowired
    AddressService addressService;

    /**
     * Registers an organization with the given details and address.
     *
     * @param organizationDetails The organization details.
     * @param addressInfo         The address information.
     * @return The registered organization details.
     */
    @Override
    public OrganizationDetails register(OrganizationDetails organizationDetails, AddressInfo addressInfo) {
        Tenant tenant = organizationDetails.getTenant() != null && organizationDetails.getTenant().getTenantName() != null
                ? tenantService.findByTenantName(organizationDetails.getTenant().getTenantName())
                : null;
        AddressInfo address = addressService.findOrCreateAddress(addressInfo);
        organizationDetails.setTenant(tenant);
        saveOrgAddressMappings(organizationDetails, address);
        return organizationRepository.save(organizationDetails);
    }

    /**
     * Creates a new {@link OrgAddressAssoc} and adds it to the given {@link OrganizationDetails}.
     *
     * @param organizationDetails the organization details
     * @param address             the address
     */
    private void saveOrgAddressMappings(OrganizationDetails organizationDetails, AddressInfo address) {
        OrgAddressAssoc orgAddressAssoc = new OrgAddressAssoc();
        OrgAddressId orgAddressId = new OrgAddressId();
        orgAddressId.setAddressType("organization");
        orgAddressAssoc.setOrgAddressId(orgAddressId);
        orgAddressAssoc.setOrganizationDetails(organizationDetails);
        orgAddressAssoc.setAddress(address);
        organizationDetails.setOrgAddress(orgAddressAssoc);
    }
}
