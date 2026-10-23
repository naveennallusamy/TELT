package com.telt;

import com.telt.config.properties.BootstrapAdminProperties;
import com.telt.entity.user.User;
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
    private final PasswordEncoder passwordEncoder;
    private final BootstrapAdminProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByPlatformRole(User.PLATFORM_SUPER_ADMIN)) {
            return;
        }
        if (isBlank(properties.password()) || isBlank(properties.email()) || isBlank(properties.mobileNumber())) {
            log.warn("No super admin exists; set TELT_ADMIN_EMAIL, TELT_ADMIN_MOBILE and TELT_ADMIN_PASSWORD to create one");
            return;
        }
        User admin = new User(properties.username(), properties.email(), Long.parseLong(properties.mobileNumber()));
        admin.setPlatformRole(User.PLATFORM_SUPER_ADMIN);
        admin.setPassword(passwordEncoder.encode(properties.password()));
        userRepository.save(admin);
        log.info("Bootstrap super admin '{}' created", properties.username());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
