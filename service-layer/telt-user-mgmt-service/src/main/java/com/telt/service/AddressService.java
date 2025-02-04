package com.telt.service;

import com.telt.entity.address.AddressInfo;

public interface AddressService {
    AddressInfo findOrCreateAddress(AddressInfo address);
}
