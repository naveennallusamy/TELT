package com.telt.dto.auth;

import com.telt.constants.MembershipType;
import com.telt.dto.user.MembershipSummary;
import com.telt.entity.user.User;
import com.telt.security.TokenService;

import java.util.List;
import java.util.UUID;

/**
 * Login / refresh response. {@code token} is the access token (name kept for existing clients). {@code memberships}
 * lists the tenants the user can switch to via {@code POST /auth/switch-tenant}.
 */
public record AuthResponse(String token, String refreshToken, String tokenType, long expiresIn, UserSummary user,
                           List<MembershipSummary> memberships) {

    public record UserSummary(UUID id, String userName, String email, List<String> authority, UUID tenantId,
                              MembershipType membershipType, boolean passwordChangeRequired) {
    }

    public static AuthResponse of(TokenService.IssuedTokens tokens, List<MembershipSummary> memberships) {
        User user = tokens.user();
        String authority = user.isSuperAdmin() ? "SUPER_ADMIN" : tokens.membershipType().name();
        return new AuthResponse(tokens.accessToken(), tokens.refreshToken(), "Bearer", tokens.expiresInSeconds(),
                new UserSummary(user.getId(), user.getUsername(), user.getEmail(), List.of(authority),
                        tokens.tenantId(), tokens.membershipType(), user.isPasswordChangeRequired()),
                memberships);
    }
}
