package com.telt;

import com.telt.config.properties.BootstrapAdminProperties;
import com.telt.constants.RoleEnum;
import com.telt.entity.role.Role;
import com.telt.entity.user.User;
import com.telt.repository.RoleRepository;
import com.telt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first super admin from {@code TELT_ADMIN_*} environment variables when none exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapAdminProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole_Name(RoleEnum.SUPER_ADMIN.name())) {
            return;
        }
        if (isBlank(properties.password()) || isBlank(properties.email()) || isBlank(properties.mobileNumber())) {
            log.warn("No super admin exists; set TELT_ADMIN_EMAIL, TELT_ADMIN_MOBILE and TELT_ADMIN_PASSWORD to create one");
            return;
        }
        Role role = roleRepository.findByName(RoleEnum.SUPER_ADMIN.name())
                .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN role missing; check Flyway migrations"));
        User admin = new User(properties.username(), properties.email(), Long.parseLong(properties.mobileNumber()), role);
        admin.setPassword(passwordEncoder.encode(properties.password()));
        userRepository.save(admin);
        log.info("Bootstrap super admin '{}' created", properties.username());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
