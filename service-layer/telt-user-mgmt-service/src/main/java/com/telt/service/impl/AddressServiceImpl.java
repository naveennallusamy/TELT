package com.telt.service.impl;

import com.telt.entity.address.AddressInfo;
import com.telt.repository.AddressRepository;
import com.telt.service.AddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AddressServiceImpl implements AddressService {

    @Autowired
    AddressRepository addressRepository;

    /**
     * Finds an existing address by the given criteria, or creates a new one if it does not exist.
     *
     * @param address the address to search for
     * @return the existing or newly created address
     */
    @Override
    public AddressInfo findOrCreateAddress(AddressInfo address) {
        return addressRepository.findByAddressLine1AndAddressLine2AndCityAndStateAndCountryAndZipCode(address.getAddressLine1(), address.getAddressLine2(), address.getCity(), address.getState(), address.getCountry(), address.getZipCode()).orElseGet(() -> addressRepository.save(address));
    }
}
