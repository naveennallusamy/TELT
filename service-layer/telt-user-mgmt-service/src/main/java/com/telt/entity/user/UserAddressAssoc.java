package com.telt.entity.user;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.telt.entity.address.AddressInfo;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Entity
@Getter
@Setter
@Table(name = "user_address_assoc")
public class UserAddressAssoc {
    @EmbeddedId
    private UserAddressId userAddressId;

    @ManyToOne
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    @JsonBackReference
    private User user;

    @ManyToOne
    @MapsId("addressId")
    @JoinColumn(name = "address_id")
    private AddressInfo address;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserAddressAssoc that = (UserAddressAssoc) o;
        return userAddressId != null && userAddressId.equals(that.userAddressId); // Compare only composite ID
    }

    @Override
    public int hashCode() {
        return Objects.hash(userAddressId);

    }
}
