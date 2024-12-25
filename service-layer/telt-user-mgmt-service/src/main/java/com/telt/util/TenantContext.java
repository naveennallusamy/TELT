package com.telt.util;

public class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ROLE = new ThreadLocal<>();

    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void setCurrentTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getCurrentRole() {
        return CURRENT_ROLE.get();
    }

    public static void setCurrentRole(String role) {
        CURRENT_ROLE.set(role);
    }

    public static boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(CURRENT_ROLE.get());
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_ROLE.remove();
    }
}