package com.telt.entity.user;

import jakarta.persistence.Embeddable;
import lombok.Data;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Data
public class UserAddressId implements Serializable {
    private Long userId;
    private Long addressId;
    private String addressType;

    // Getters, Setters, equals, and hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserAddressId that = (UserAddressId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(addressId, that.addressId) && Objects.equals(addressType, that.addressType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, addressId);
    }
}
