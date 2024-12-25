package com.telt.service;

import com.telt.entity.Role;
import com.telt.entity.Tenant;
import com.telt.entity.User;
import com.telt.repository.UserRepository;
import com.telt.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

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
    private JwtUtil jwtUtil;

    /**
     * @param email
     * @param mobile
     * @param password
     * @param roleName
     * @param tenantName
     * @return
     */
    @Override
    public User registerUser(String email, String mobile, String password, String roleName, Optional<String> tenantName) {
        Role role = roleService.findByName(roleName);

        User user = new User();
        user.setEmail(email);
        user.setMobile(mobile);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);

        // Set tenant only for non-super admin users
        tenantName.ifPresent(name -> {
            Tenant tenant = tenantService.findOrCreateTenant(name, null);
            user.setTenant(tenant);
        });

        return userRepository.save(user);
    }

    /**
     * @param identifier
     * @param newPassword
     * @return
     */
    @Override
    public User changePassword(String identifier, String newPassword) {
        User user = userRepository.findByEmailOrMobile(identifier, identifier)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPassword(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }

    /**
     * @param emailOrMobile
     * @param password
     * @return
     */
    @Override
    public String login(String emailOrMobile, String password) {
        User user = userRepository.findByEmailOrMobile(emailOrMobile, emailOrMobile)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        return jwtUtil.generateToken(user.getEmail());
    }
}
