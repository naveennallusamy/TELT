package com.telt.entity.organization;

import jakarta.persistence.Embeddable;
import lombok.Data;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Data
public class OrgAddressId implements Serializable {
    private Long orgId;
    private Long addressId;
    private String addressType;

    // Getters, Setters, equals, and hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrgAddressId that = (OrgAddressId) o;
        return Objects.equals(orgId, that.orgId) && Objects.equals(addressId, that.addressId) && Objects.equals(addressType, that.addressType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, addressId);
    }
}
