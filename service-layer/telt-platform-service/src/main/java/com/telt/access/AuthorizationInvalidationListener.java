package com.telt.access;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Listens on the {@value #CHANNEL} PostgreSQL channel, fed by triggers on memberships, role assignments, departments
 * and tenant settings, and evicts the affected cached {@link AuthorizationSnapshot}s on every instance. Uses its own
 * connection so it never holds a pool slot; after a reconnect the whole cache is dropped since notifications may have
 * been missed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "telt.cache.authz-invalidation", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class AuthorizationInvalidationListener implements SmartLifecycle {

    static final String CHANNEL = "telt_authz";
    private static final int POLL_MILLIS = 5_000;
    private static final long MAX_BACKOFF_MILLIS = 30_000;

    private final AuthorizationService authorizationService;
    private final Environment environment;

    private volatile boolean running;
    private volatile Connection connection;
    private Thread worker;

    @Override
    public void start() {
        running = true;
        worker = Thread.ofPlatform().daemon().name("authz-invalidation").start(this::listen);
    }

    @Override
    public void stop() {
        running = false;
        Connection current = connection;
        if (current != null) {
            try {
                current.close();
            } catch (SQLException ignored) {
                // shutting down
            }
        }
        if (worker != null) {
            worker.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void listen() {
        long backoff = 1_000;
        while (running) {
            try (Connection c = DriverManager.getConnection(environment.getRequiredProperty("spring.datasource.url"),
                    environment.getProperty("spring.datasource.username"),
                    environment.getProperty("spring.datasource.password"))) {
                connection = c;
                try (Statement statement = c.createStatement()) {
                    statement.execute("LISTEN " + CHANNEL);
                }
                authorizationService.evictAll();
                backoff = 1_000;
                PGConnection pg = c.unwrap(PGConnection.class);
                while (running) {
                    PGNotification[] notifications = pg.getNotifications(POLL_MILLIS);
                    if (notifications != null) {
                        for (PGNotification notification : notifications) {
                            apply(notification.getParameter());
                        }
                    }
                }
            } catch (SQLException e) {
                if (!running) {
                    return;
                }
                log.warn("Authorization invalidation listener disconnected, retrying in {} ms: {}", backoff,
                        e.getMessage());
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                backoff = Math.min(backoff * 2, MAX_BACKOFF_MILLIS);
            } finally {
                connection = null;
            }
        }
    }

    /**
     * Payload is {@code <tenantId>:<userId>} or {@code <tenantId>:*}.
     */
    void apply(String payload) {
        int separator = payload.indexOf(':');
        if (separator < 0) {
            authorizationService.evictAll();
            return;
        }
        try {
            UUID tenantId = UUID.fromString(payload.substring(0, separator));
            String user = payload.substring(separator + 1);
            if ("*".equals(user)) {
                authorizationService.evictTenant(tenantId);
            } else {
                authorizationService.evictUser(tenantId, UUID.fromString(user));
            }
        } catch (IllegalArgumentException e) {
            authorizationService.evictAll();
        }
    }
}
