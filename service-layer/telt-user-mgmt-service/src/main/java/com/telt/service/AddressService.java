package com.telt.service;

import com.telt.entity.AddressInfo;

public interface AddressService {
    AddressInfo findOrCreateAddress(AddressInfo address);
}
