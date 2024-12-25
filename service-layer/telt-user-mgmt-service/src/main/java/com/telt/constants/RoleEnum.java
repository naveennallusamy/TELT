package com.telt.constants;

public enum RoleEnum {
    SUPER_ADMIN, // Super Admin has global access
    TENANT_ADMIN, // Tenant Admin has access to their tenant
    USER; // Normal user belongs to a tenant
}
