package com.telt.security;

import com.telt.config.properties.JwtProperties;
import com.telt.constants.MembershipType;
import com.telt.constants.UserStatus;
import com.telt.entity.auth.RefreshToken;
import com.telt.entity.tenant.TenantMembership;
import com.telt.entity.user.User;
import com.telt.error.ApiException;
import com.telt.error.ErrorCode;
import com.telt.repository.RefreshTokenRepository;
import com.telt.repository.TenantMembershipRepository;
import com.telt.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues short-lived RS256 access tokens bound to one active tenant, and rotating opaque refresh tokens.
 * Claims: {@code sub} user id, {@code tid} active tenant, {@code roles} = [SUPER_ADMIN] or [membership type],
 * {@code pcr} password change required. Permissions are resolved server-side, not carried in the token.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TenantMembershipRepository membershipRepository;

    public record IssuedTokens(String accessToken, String refreshToken, long expiresInSeconds, User user,
                               UUID tenantId, MembershipType membershipType) {
    }

    /**
     * @param membership active membership to bind the session to; {@code null} for super admins
     */
    @Transactional
    public IssuedTokens issue(User user, TenantMembership membership) {
        return issue(user, membership, UUID.randomUUID());
    }

    /**
     * Rotates a refresh token. Presenting an already-rotated token is treated as theft and revokes the family.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public IssuedTokens rotate(String rawRefreshToken) {
        Instant now = Instant.now();
        RefreshToken current = refreshTokenRepository.findByTokenHash(SecureTokens.sha256(rawRefreshToken))
                .orElseThrow(TokenService::invalidRefreshToken);
        if (current.getRevokedAt() != null) {
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            throw invalidRefreshToken();
        }
        if (!current.isActive(now)) {
            throw invalidRefreshToken();
        }
        User user = current.getUser();
        Optional<TenantMembership> membership = current.getTenantId() == null ? Optional.empty()
                : membershipRepository.findByTenantIdAndUser_Id(current.getTenantId(), user.getId())
                .filter(TenantMembership::isActive);
        boolean sessionValid = user.getStatus() == UserStatus.ACTIVE && !user.isLocked(now)
                && (user.isSuperAdmin() || membership.isPresent());
        if (!sessionValid) {
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            throw invalidRefreshToken();
        }
        current.setRevokedAt(now);
        return issue(user, membership.orElse(null), current.getFamilyId());
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(SecureTokens.sha256(rawRefreshToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    private IssuedTokens issue(User user, TenantMembership membership, UUID familyId) {
        Instant now = Instant.now();
        UUID tenantId = membership == null ? null : membership.getTenantId();
        MembershipType type = membership == null ? null : membership.getMembershipType();
        String role = user.isSuperAdmin() ? TenantContext.SUPER_ADMIN : type.name();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim(TenantContext.ROLES_CLAIM, List.of(role))
                .claim(PasswordChangeRequiredFilter.CLAIM, user.isPasswordChangeRequired());
        if (tenantId != null) {
            claims.claim(TenantContext.TENANT_CLAIM, tenantId.toString());
        }
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build())).getTokenValue();

        String rawRefresh = SecureTokens.newToken();
        RefreshToken refresh = new RefreshToken();
        refresh.setTokenHash(SecureTokens.sha256(rawRefresh));
        refresh.setFamilyId(familyId);
        refresh.setTenantId(tenantId);
        refresh.setUser(user);
        refresh.setCreatedAt(now);
        refresh.setExpiresAt(now.plus(properties.refreshTokenTtl()));
        refreshTokenRepository.save(refresh);

        return new IssuedTokens(accessToken, rawRefresh, properties.accessTokenTtl().toSeconds(), user, tenantId, type);
    }

    private static ApiException invalidRefreshToken() {
        return new ApiException(ErrorCode.INVALID_TOKEN, "Refresh token is invalid or expired");
    }
}
