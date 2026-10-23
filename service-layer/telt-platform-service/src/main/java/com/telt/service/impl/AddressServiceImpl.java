package com.telt.service.impl;

import com.telt.entity.address.AddressInfo;
import com.telt.repository.AddressRepository;
import com.telt.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;

    /**
     * Returns the existing address with identical fields, or saves and returns a new one.
     */
    @Override
    @Transactional
    public AddressInfo findOrCreateAddress(AddressInfo address) {
        return addressRepository.findByAddressLine1AndAddressLine2AndCityAndStateAndCountryAndZipCode(
                        address.getAddressLine1(), address.getAddressLine2(), address.getCity(), address.getState(),
                        address.getCountry(), address.getZipCode())
                .orElseGet(() -> addressRepository.save(address));
    }
}
