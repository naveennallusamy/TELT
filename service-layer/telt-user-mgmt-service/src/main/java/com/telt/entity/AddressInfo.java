package com.telt.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "address_info", uniqueConstraints = {@UniqueConstraint(columnNames = {"addressLine1", "addressLine2", "city", "state", "country", "zipCode"})})
public class AddressInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long addressId;

    private String addressLine1;

    private String addressLine2;

    private String city;

    private String state;

    private String country;

    private String zipCode;
}
