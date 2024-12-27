package com.telt.service.impl;

import com.telt.entity.Role;
import com.telt.entity.Tenant;
import com.telt.entity.User;
import com.telt.repository.UserRepository;
import com.telt.service.RoleService;
import com.telt.service.TenantService;
import com.telt.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

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

    @Override
    public User registerUser(User user) {
        Role role = roleService.findByName(user.getRole().getName());
        Tenant tenant = null;
        if (user.getTenant() != null && user.getTenant().getTenantName() != null) {
            tenant = tenantService.findByTenantName(user.getTenant().getTenantName());
        }
        user.setRole(role);
        user.setTenant(tenant);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    /**
     * @return
     */
    @Override
    public List<User> findAll() {
        return userRepository.findAll();
    }
}
