package com.telt.persistence;

import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class RlsConfig {

    /**
     * RLS does not apply to superusers, roles with BYPASSRLS, or the owner of the tables (the migration user).
     */
    private static final String RLS_BYPASSED = """
            SELECT r.rolsuper OR r.rolbypassrls OR pg_has_role(current_user, c.relowner, 'MEMBER')
            FROM pg_roles r, pg_class c
            WHERE r.rolname = current_user AND c.oid = 'tenant_memberships'::regclass
            """;

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new RlsJpaTransactionManager(emf);
    }

    @Bean
    RlsEnforcementCheck rlsEnforcementCheck(JdbcTemplate jdbcTemplate,
                                            @Value("${telt.security.rls.require-enforced:false}") boolean required) {
        return new RlsEnforcementCheck(jdbcTemplate, required);
    }

    record RlsEnforcementCheck(JdbcTemplate jdbcTemplate, boolean required) {

        @EventListener(ApplicationReadyEvent.class)
        public void verify() {
            if (!Boolean.TRUE.equals(jdbcTemplate.queryForObject(RLS_BYPASSED, Boolean.class))) {
                return;
            }
            String message = "Database user bypasses row-level security; connect as a non-owner application role "
                    + "(see README: Database roles)";
            if (required) {
                throw new IllegalStateException(message);
            }
            log.warn(message);
        }
    }
}
