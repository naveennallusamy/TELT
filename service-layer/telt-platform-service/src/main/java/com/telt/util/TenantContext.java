package com.telt.util;

import com.telt.constants.RoleEnum;
import com.telt.error.ApiException;
import com.telt.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only view of the caller's identity and tenant, derived from the validated access token held in the
 * {@link SecurityContextHolder}. Nothing is stored in a ThreadLocal of our own, so nothing can leak between
 * requests served by the same pooled thread.
 */
public final class TenantContext {

    public static final String TENANT_CLAIM = "tid";
    public static final String ROLES_CLAIM = "roles";

    private TenantContext() {
    }

    public static UUID getCurrentTenant() {
        return currentJwt().map(jwt -> jwt.getClaimAsString(TENANT_CLAIM)).map(UUID::fromString).orElse(null);
    }

    public static Optional<UUID> getCurrentUserId() {
        return currentJwt().map(Jwt::getSubject).map(UUID::fromString);
    }

    public static UUID requireCurrentUserId() {
        return getCurrentUserId().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Authentication required"));
    }

    public static boolean isSuperAdmin() {
        return hasRole(RoleEnum.SUPER_ADMIN);
    }

    public static boolean hasRole(RoleEnum role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> role.name().equals(a.getAuthority()));
    }

    private static Optional<Jwt> currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return Optional.of(token.getToken());
        }
        return Optional.empty();
    }
}
