package com.telt.service.impl;

import com.telt.dto.LoginResponseDTO;
import com.telt.entity.auth.AuthResponse;
import com.telt.entity.auth.LoginRequest;
import com.telt.entity.auth.PasswordResetToken;
import com.telt.entity.user.User;
import com.telt.repository.PasswordResetTokenRepository;
import com.telt.repository.UserRepository;
import com.telt.service.AuthService;
import com.telt.util.JwtUtil;
import com.telt.util.TenantContext;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNumeric;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final JwtUtil jwtUtil;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(UserRepository userRepository, PasswordResetTokenRepository tokenRepository, JwtUtil jwtUtil, JavaMailSender mailSender, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.jwtUtil = jwtUtil;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Authenticates user and generates a JWT token.
     *
     * @param loginRequest User credentials
     * @return ResponseEntity containing the generated JWT token
     * @throws BadCredentialsException if the credentials are invalid
     */
    @Override
    public AuthResponse login(LoginRequest loginRequest) throws BadCredentialsException {
        User user = userRepository.findByEmailOrMobileNumberOrUsername(loginRequest.getUserName(), isNumeric(loginRequest.getUserName()) ? Long.valueOf(loginRequest.getUserName()) : null, loginRequest.getUserName()).orElseThrow(() -> new BadCredentialsException("Invalid Username"));

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid Password");
        }

        AuthResponse authResponse = new AuthResponse();
        authResponse.setToken(jwtUtil.generateToken(user.getEmail(), user.getRole().getName(), TenantContext.getCurrentTenant()));
        authResponse.setUser(new LoginResponseDTO(user.getUsername(), "/img/avatars/thumb-1.jpg", user.getEmail(), user.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList())));

        return authResponse;

    }

    /**
     * Initiates the forgot password process by generating a password reset token
     * and sending a reset link to the user's email or mobile number.
     *
     * @param username User's email address, mobile number, or username
     * @throws Exception if the user is not found
     */
    @Override
    public void forgotPassword(String username) throws Exception {
        Optional<User> userOptional = userRepository.findByEmailOrMobileNumberOrUsername(username, isNumeric(username) ? Long.valueOf(username) : null, username);

        User user = userOptional.orElseThrow(() -> new Exception("User not found"));

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(UUID.randomUUID().toString());
        resetToken.setExpirationDate(LocalDateTime.now().plusHours(1));
        resetToken.setUser(user);

        tokenRepository.save(resetToken);

        sendPasswordResetEmail(user.getEmail(), resetToken.getToken());
    }

    /**
     * Sends a password reset link to the user's email address.
     *
     * @param email The user's email address
     * @param token The password reset token
     */
    private void sendPasswordResetEmail(String email, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Password Reset Request");
        message.setText("To reset your password, click the following link: " + "http://localhost:8080/telt/api/auth/reset-password?token=" + token);
        mailSender.send(message);
    }

    /**
     * Resets the user's password if the given token is valid and not expired.
     * <p>
     * The {@code token} is expected to be a valid password reset token that has not expired.
     * The {@code newPassword} will be used to set the user's new password.
     * <p>
     * If the token is invalid or has expired, an exception is thrown.
     *
     * @param token       The password reset token
     * @param newPassword The new password for the user
     * @throws Exception If the token is invalid or has expired
     */
    @Override
    public void resetPassword(String token, String newPassword) throws Exception {
        Optional<PasswordResetToken> tokenOptional = tokenRepository.findByToken(token);

        if (tokenOptional.isEmpty()) {
            throw new Exception("Invalid or expired token");
        }

        PasswordResetToken resetToken = tokenOptional.get();

        if (resetToken.getExpirationDate().isBefore(LocalDateTime.now())) {
            throw new Exception("Token has expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));  // Encrypt the new password
        userRepository.save(user);
        tokenRepository.delete(resetToken);
    }

    /**
     * Changes the password for a user.
     * <p>
     * This method verifies the old password and updates it with the new password
     * for the specified user. If the user is not found or the old password does not
     * match, an exception is thrown.
     *
     * @param userId      The identifier of the user whose password is to be changed
     * @param oldPassword The current password of the user
     * @param newPassword The new password to set for the user
     * @throws Exception If the user is not found or the old password is incorrect
     */
    @Override
    public void changePassword(String userId, String oldPassword, String newPassword) throws Exception {
        // Fetch user from the database
        User user = userRepository.findById(userId).orElseThrow(() -> new Exception("User not found"));

        // Verify the old password
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new Exception("Old password is incorrect");
        }

        // Update password with new password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}

