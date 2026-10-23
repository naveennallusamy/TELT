package com.telt.constants;

import java.util.UUID;

/**
 * Reserved tenant that owns platform (super-admin) question banks. It has no members and is hidden from tenant APIs.
 */
public final class PlatformTenant {

    public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private PlatformTenant() {
    }

    public static boolean is(UUID tenantId) {
        return ID.equals(tenantId);
    }
}
