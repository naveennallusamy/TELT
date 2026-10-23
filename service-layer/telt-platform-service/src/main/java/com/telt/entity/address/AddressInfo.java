package com.telt.entity.address;

import com.telt.util.AuditableEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;
import lombok.Data;

@Entity
@Data
@Table(name = "address_info", uniqueConstraints = {@UniqueConstraint(columnNames = {"addressLine1", "addressLine2", "city", "state", "country", "zipCode"})})
public class AddressInfo extends AuditableEntity {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID addressId;

    private String addressLine1;

    private String addressLine2;

    private String city;

    private String state;

    private String country;

    private String zipCode;
}
