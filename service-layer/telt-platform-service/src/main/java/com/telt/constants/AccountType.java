package com.telt.constants;

/**
 * Account kinds accepted when creating users: a platform super admin or a member of one tenant.
 */
public enum AccountType {
    SUPER_ADMIN,
    TENANT_ADMIN,
    SUB_ADMIN,
    STUDENT;

    public MembershipType toMembershipType() {
        return this == SUPER_ADMIN ? null : MembershipType.valueOf(name());
    }
}
