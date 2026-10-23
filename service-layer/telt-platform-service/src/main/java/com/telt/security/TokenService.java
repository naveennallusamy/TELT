package com.telt.security;

import com.telt.config.properties.JwtProperties;
import com.telt.constants.UserStatus;
import com.telt.entity.auth.RefreshToken;
import com.telt.entity.user.User;
import com.telt.error.ApiException;
import com.telt.error.ErrorCode;
import com.telt.repository.RefreshTokenRepository;
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
import java.util.UUID;

/**
 * Issues short-lived RS256 access tokens and rotating opaque refresh tokens.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final RefreshTokenRepository refreshTokenRepository;

    public record IssuedTokens(String accessToken, String refreshToken, long expiresInSeconds, User user) {
    }

    @Transactional
    public IssuedTokens issue(User user) {
        return issue(user, UUID.randomUUID());
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
        if (user.getStatus() != UserStatus.ACTIVE || user.isLocked(now)) {
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            throw invalidRefreshToken();
        }
        current.setRevokedAt(now);
        return issue(user, current.getFamilyId());
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

    private IssuedTokens issue(User user, UUID familyId) {
        Instant now = Instant.now();
        Instant accessExpiry = now.plus(properties.accessTokenTtl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(accessExpiry)
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim(TenantContext.ROLES_CLAIM, List.of(user.getRole().getName()))
                .claim(PasswordChangeRequiredFilter.CLAIM, user.isPasswordChangeRequired());
        if (user.getTenant() != null) {
            claims.claim(TenantContext.TENANT_CLAIM, user.getTenant().getTenantId().toString());
        }
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build())).getTokenValue();

        String rawRefresh = SecureTokens.newToken();
        RefreshToken refresh = new RefreshToken();
        refresh.setTokenHash(SecureTokens.sha256(rawRefresh));
        refresh.setFamilyId(familyId);
        refresh.setUser(user);
        refresh.setCreatedAt(now);
        refresh.setExpiresAt(now.plus(properties.refreshTokenTtl()));
        refreshTokenRepository.save(refresh);

        return new IssuedTokens(accessToken, rawRefresh, properties.accessTokenTtl().toSeconds(), user);
    }

    private static ApiException invalidRefreshToken() {
        return new ApiException(ErrorCode.INVALID_TOKEN, "Refresh token is invalid or expired");
    }
}
