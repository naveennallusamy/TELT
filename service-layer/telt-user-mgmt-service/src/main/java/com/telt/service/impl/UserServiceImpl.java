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

    /**
     * Registers a new user with the provided user details.
     * <p>
     * First, it finds the role and tenant by the given name, and associates them with the user.
     * Then, it encodes the provided password and associates it with the user.
     * It associates the user with the created addresses, and saves the mapping.
     * Finally, it saves the user to the repository and returns it.
     *
     * @param user             the user to register
     * @param permanentAddress the permanent address of the user
     * @param currentAddress   the current address of the user
     * @return the registered user
     */
    @Override
    @Transactional
    public User registerUser(User user, AddressInfo permanentAddress, AddressInfo currentAddress) {
        Role role = roleService.findByName(user.getRole().getName());
        Tenant tenant = user.getTenant() != null && user.getTenant().getTenantName() != null ? tenantService.findByTenantName(user.getTenant().getTenantName()) : null;
        user.setRole(role);
        user.setTenant(tenant);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.getPersonalInfo().setUser(user);
        AddressInfo current = addressService.findOrCreateAddress(currentAddress);
        AddressInfo permanent = addressService.findOrCreateAddress(permanentAddress);
        saveUserAddressMappings(user, current, permanent);
        return userRepository.save(user);
    }

    /**
     * Saves the address mappings for a user by creating and associating address records.
     * <p>
     * This method creates mappings for both the current and permanent addresses of the user.
     * It associates these mappings with the user and adds them to the user's address details.
     *
     * @param user      the user for whom the address mappings are created
     * @param current   the current address information to be mapped
     * @param permanent the permanent address information to be mapped
     */
    private void saveUserAddressMappings(User user, AddressInfo current, AddressInfo permanent) {
        Set<UserAddressAssoc> mappings = new HashSet<>();
        mappings.add(createUserAddressMapping(user, current, "current"));
        mappings.add(createUserAddressMapping(user, permanent, "permanent"));
        user.getUserAddressDetails().addAll(mappings);
    }

    /**
     * Creates a user address mapping with the specified address type.
     * <p>
     * This method initializes a new {@link UserAddressAssoc} instance,
     * sets its composite key with the given address type, and associates
     * the address and user with the mapping.
     *
     * @param user        the user to associate with the address
     * @param address     the address to associate with the user
     * @param addressType the type of address (e.g., "current" or "permanent")
     * @return the created user address mapping
     */
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
     * Retrieves a list of all users from the repository.
     *
     * @return a list of all {@link User} objects.
     */
    @Override
    public List<User> findAll() {
        return userRepository.findAll();
    }

}
