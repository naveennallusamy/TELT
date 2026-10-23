package com.telt.persistence;

import java.util.function.Supplier;

/**
 * Marks work that must see rows of every tenant (authentication flows that run before a tenant is known and
 * internal authorization lookups). Everything else runs with the caller's tenant from the access token.
 */
public final class RlsContext {

    private static final ThreadLocal<Boolean> SYSTEM = new ThreadLocal<>();

    private RlsContext() {
    }

    public static boolean isSystem() {
        return Boolean.TRUE.equals(SYSTEM.get());
    }

    public static <T> T asSystem(Supplier<T> work) {
        Boolean previous = SYSTEM.get();
        SYSTEM.set(Boolean.TRUE);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                SYSTEM.remove();
            } else {
                SYSTEM.set(previous);
            }
        }
    }

    public static void asSystem(Runnable work) {
        asSystem(() -> {
            work.run();
            return null;
        });
    }
}
