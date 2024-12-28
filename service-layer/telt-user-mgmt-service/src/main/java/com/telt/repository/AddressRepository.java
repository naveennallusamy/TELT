package com.telt.repository;

import com.telt.entity.AddressInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<AddressInfo, String> {
    Optional<AddressInfo> findByAddressLine1AndAddressLine2AndCityAndStateAndCountryAndZipCode(
            String addressLine1, String addressLine2, String city, String state, String country, String zipCode
    );
}
