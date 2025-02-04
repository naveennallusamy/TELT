package com.telt;

import com.telt.entity.role.Role;
import com.telt.entity.user.User;
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
                .orElseGet(() -> roleRepository.save(new Role("SUPER_ADMIN")));

        if (!userRepository.existsByUsername("teltadmin")) {
            User superAdmin = new User("teltadmin", "nsnaveenit@gmail.com", 8122291751L, superAdminRole);
            superAdmin.setPassword(passwordEncoder.encode("Welcome321!"));
            userRepository.save(superAdmin);
        }

    }
}
