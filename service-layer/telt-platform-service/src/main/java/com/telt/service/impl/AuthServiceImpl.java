package com.telt.service.impl;

import com.telt.config.properties.AccountProperties;
import com.telt.constants.UserStatus;
import com.telt.dto.auth.AuthResponse;
import com.telt.dto.auth.LoginRequest;
import com.telt.entity.auth.PasswordResetToken;
import com.telt.entity.user.User;
import com.telt.error.ApiException;
import com.telt.error.ErrorCode;
import com.telt.repository.PasswordResetTokenRepository;
import com.telt.repository.UserRepository;
import com.telt.security.SecureTokens;
import com.telt.security.TokenService;
import com.telt.service.AuthService;
import com.telt.service.notification.PasswordResetNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetNotifier passwordResetNotifier;
    private final AccountProperties accountProperties;

    @Override
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest request) {
        Instant now = Instant.now();
        User user = findByIdentifier(request.userName()).orElseThrow(AuthServiceImpl::invalidCredentials);
        if (user.isLocked(now)) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED,
                    "Account is temporarily locked. Try again later or reset your password");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            registerFailedLogin(user, now);
            throw invalidCredentials();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ACCOUNT_INACTIVE, "Account is inactive");
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        return AuthResponse.of(tokenService.issue(user));
    }

    @Override
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String refreshToken) {
        return AuthResponse.of(tokenService.rotate(refreshToken));
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        tokenService.revoke(refreshToken);
    }

    @Override
    @Transactional
    public void logoutAll(UUID userId) {
        tokenService.revokeAll(userId);
    }

    @Override
    @Transactional
    public void forgotPassword(String emailOrMobile) {
        findByIdentifier(emailOrMobile)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .ifPresent(this::sendPasswordResetLink);
    }

    @Override
    @Transactional
    public void sendPasswordResetLink(User user) {
        Instant now = Instant.now();
        resetTokenRepository.invalidateOutstanding(user.getId(), now);
        String rawToken = SecureTokens.newToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setTokenHash(SecureTokens.sha256(rawToken));
        token.setUser(user);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(accountProperties.passwordResetTtl()));
        resetTokenRepository.save(token);
        String link = UriComponentsBuilder.fromUriString(accountProperties.passwordResetUrl())
                .queryParam("token", rawToken).build().toUriString();
        passwordResetNotifier.sendPasswordResetLink(user.getEmail(), link);
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        Instant now = Instant.now();
        PasswordResetToken resetToken = resetTokenRepository.findByTokenHash(SecureTokens.sha256(token))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN, "Reset link is invalid or expired"));
        resetToken.setUsedAt(now);
        User user = resetToken.getUser();
        applyNewPassword(user, newPassword, now);
        tokenService.revokeAll(user.getId());
    }

    @Override
    @Transactional
    public AuthResponse changePassword(UUID userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw ApiException.badRequest("New password must differ from the current password");
        }
        applyNewPassword(user, newPassword, Instant.now());
        tokenService.revokeAll(user.getId());
        return AuthResponse.of(tokenService.issue(user));
    }

    private void applyNewPassword(User user, String newPassword, Instant now) {
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(false);
        user.setPasswordChangedAt(now);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
    }

    private void registerFailedLogin(User user, Instant now) {
        int attempts = user.getFailedLoginAttempts() + 1;
        if (attempts >= accountProperties.maxFailedLogins()) {
            user.setLockedUntil(now.plus(accountProperties.lockoutDuration()));
            attempts = 0;
        }
        user.setFailedLoginAttempts(attempts);
    }

    private java.util.Optional<User> findByIdentifier(String identifier) {
        String value = identifier.trim();
        Long mobile = value.matches("\\d{1,18}") ? Long.valueOf(value) : null;
        List<User> matches = userRepository.findByLoginIdentifier(value, mobile);
        return matches.size() == 1 ? java.util.Optional.of(matches.getFirst()) : java.util.Optional.empty();
    }

    private static ApiException invalidCredentials() {
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials");
    }
}
