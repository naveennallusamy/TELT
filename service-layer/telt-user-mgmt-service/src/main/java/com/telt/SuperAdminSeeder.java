package com.telt;

import com.telt.entity.Role;
import com.telt.entity.User;
import com.telt.repository.RoleRepository;
import com.telt.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SuperAdminSeeder implements CommandLineRunner {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * @param args
     * @throws Exception
     */
    @Override
    public void run(String... args) throws Exception {
        Role superAdminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role() {{
                    setName("SUPER_ADMIN");
                }}));

        if (!userRepository.existsByUsername("teltadmin")) {
            User superAdmin = new User();
            superAdmin.setPassword(passwordEncoder.encode("Welcome321!"));
            superAdmin.setUsername("teltadmin");
            superAdmin.setEmail("nsnaveenit@gmail.com");
            superAdmin.setMobileNumber(8122291751L);
            superAdmin.setRole(superAdminRole);
            userRepository.save(superAdmin);
        }

    }
}
