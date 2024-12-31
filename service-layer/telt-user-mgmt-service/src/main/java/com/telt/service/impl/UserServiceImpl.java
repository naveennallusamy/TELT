package com.telt.service.impl;

import com.telt.entity.address.AddressInfo;
import com.telt.entity.role.Role;
import com.telt.entity.tenant.Tenant;
import com.telt.entity.user.User;
import com.telt.entity.user.UserAddressAssoc;
import com.telt.entity.user.UserAddressId;
import com.telt.repository.UserAddressMappingRepository;
import com.telt.repository.UserRepository;
import com.telt.service.AddressService;
import com.telt.service.RoleService;
import com.telt.service.TenantService;
import com.telt.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleService roleService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AddressService addressService;

    @Autowired
    private UserAddressMappingRepository userAddressMappingRepository;

    @Override
    @Transactional
    public User registerUser(User user, AddressInfo permanentAddress, AddressInfo currentAddress) {
        Role role = roleService.findByName(user.getRole().getName());
        Tenant tenant = null;
        if (user.getTenant() != null && user.getTenant().getTenantName() != null) {
            tenant = tenantService.findByTenantName(user.getTenant().getTenantName());
        }
        user.setRole(role);
        user.setTenant(tenant);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.getPersonalInfo().setUser(user);
        //user = userRepository.save(user);
        AddressInfo current = addressService.findOrCreateAddress(currentAddress);
        AddressInfo permanent = addressService.findOrCreateAddress(permanentAddress);
        saveUserAddressMappings(user, current, permanent);
        user = userRepository.save(user);
        return user;
    }

    private void saveUserAddressMappings(User user, AddressInfo current, AddressInfo permanent) {
        Set<UserAddressAssoc> mappings = new HashSet<>();
        mappings.add(createUserAddressMapping(user, current, "current"));
        mappings.add(createUserAddressMapping(user, permanent, "permanent"));
        user.getUserAddressDetails().addAll(mappings);
    }

    private UserAddressAssoc createUserAddressMapping(User user, AddressInfo address, String addressType) {
        UserAddressAssoc mapping = new UserAddressAssoc();
        UserAddressId addressId = new UserAddressId();
        addressId.setAddressType(addressType);
        mapping.setUserAddressId(addressId);
        mapping.setUser(user);
        mapping.setAddress(address);
        return mapping;
    }

    /**
     * @return
     */
    @Override
    public List<User> findAll() {
        return userRepository.findAll();
    }
}
