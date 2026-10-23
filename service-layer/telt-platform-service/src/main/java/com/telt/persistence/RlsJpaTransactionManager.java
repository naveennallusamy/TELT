package com.telt.persistence;

import com.telt.util.TenantContext;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.PreparedStatement;
import java.util.UUID;

/**
 * Publishes the caller's tenant, user and bypass flag as transaction-local PostgreSQL settings
 * ({@code telt.tenant_id}, {@code telt.user_id}, {@code telt.bypass_rls}) that the row-level security policies read.
 * {@code set_config(..., true)} scopes them to the transaction, so pooled connections never carry them over.
 */
public class RlsJpaTransactionManager extends JpaTransactionManager {

    private static final String SET_CONTEXT = "SELECT set_config('telt.tenant_id', ?, true), "
            + "set_config('telt.user_id', ?, true), set_config('telt.bypass_rls', ?, true)";

    public RlsJpaTransactionManager(EntityManagerFactory emf) {
        super(emf);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        EntityManagerHolder holder = (EntityManagerHolder) TransactionSynchronizationManager
                .getResource(obtainEntityManagerFactory());
        if (holder == null) {
            return;
        }
        UUID tenantId = TenantContext.getCurrentTenant();
        String userId = TenantContext.getCurrentUserId().map(UUID::toString).orElse("");
        boolean bypass = RlsContext.isSystem() || TenantContext.isSuperAdmin();
        holder.getEntityManager().unwrap(Session.class).doWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SET_CONTEXT)) {
                statement.setString(1, tenantId == null ? "" : tenantId.toString());
                statement.setString(2, userId);
                statement.setString(3, bypass ? "on" : "off");
                statement.execute();
            }
        });
    }
}
