package com.telt.dto.auth;

import com.telt.entity.user.User;
import com.telt.security.TokenService;

import java.util.List;
import java.util.UUID;

/**
 * Login / refresh response. {@code token} is the access token (name kept for existing clients).
 */
public record AuthResponse(String token, String refreshToken, String tokenType, long expiresIn, UserSummary user) {

    public record UserSummary(UUID id, String userName, String email, List<String> authority, UUID tenantId,
                              boolean passwordChangeRequired) {
    }

    public static AuthResponse of(TokenService.IssuedTokens tokens) {
        User user = tokens.user();
        return new AuthResponse(tokens.accessToken(), tokens.refreshToken(), "Bearer", tokens.expiresInSeconds(),
                new UserSummary(user.getId(), user.getUsername(), user.getEmail(), List.of(user.getRole().getName()),
                        user.getTenant() == null ? null : user.getTenant().getTenantId(),
                        user.isPasswordChangeRequired()));
    }
}
