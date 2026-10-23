package com.telt.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Flyway migrates as the container superuser (schema owner) while the application connects as the unprivileged
 * {@value #APP_ROLE} role, mirroring production so row-level security is actually enforced in tests.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String APP_ROLE = "telt_app";

    @Bean
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17-alpine").withInitScript("db/create-app-role.sql");
    }

    @Bean
    DynamicPropertyRegistrar postgresProperties(PostgreSQLContainer postgres) {
        return registry -> {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", () -> APP_ROLE);
            registry.add("spring.datasource.password", () -> APP_ROLE);
            registry.add("spring.flyway.url", postgres::getJdbcUrl);
            registry.add("spring.flyway.user", postgres::getUsername);
            registry.add("spring.flyway.password", postgres::getPassword);
            registry.add("spring.flyway.placeholders.app_role", () -> APP_ROLE);
            registry.add("telt.security.rls.require-enforced", () -> "true");
        };
    }

    @Bean
    @Primary
    CapturingPasswordResetNotifier capturingPasswordResetNotifier() {
        return new CapturingPasswordResetNotifier();
    }
}
