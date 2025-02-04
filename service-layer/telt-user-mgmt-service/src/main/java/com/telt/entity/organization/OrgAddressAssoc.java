package com.telt.entity.organization;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.telt.entity.address.AddressInfo;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Entity
@Getter
@Setter
@Table(name = "org_address_assoc")
public class OrgAddressAssoc {
    @EmbeddedId
    private OrgAddressId orgAddressId;

    @ManyToOne
    @MapsId("orgId")
    @JoinColumn(name = "org_id")
    @JsonBackReference
    private OrganizationDetails organizationDetails;

    @ManyToOne
    @MapsId("addressId")
    @JoinColumn(name = "address_id")
    private AddressInfo address;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrgAddressAssoc that = (OrgAddressAssoc) o;
        return orgAddressId != null && orgAddressId.equals(that.orgAddressId); // Compare only composite ID
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgAddressId);

    }
}
