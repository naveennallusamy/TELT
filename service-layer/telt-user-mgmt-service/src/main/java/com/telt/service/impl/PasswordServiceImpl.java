package com.telt.service.impl;

import com.telt.entity.PasswordResetToken;
import com.telt.entity.User;
import com.telt.repository.PasswordResetTokenRepository;
import com.telt.repository.UserRepository;
import com.telt.service.PasswordService;
import com.telt.util.JwtUtil;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordServiceImpl implements PasswordService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final JwtUtil jwtUtil;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    public PasswordServiceImpl(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                               JwtUtil jwtUtil, JavaMailSender mailSender, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.jwtUtil = jwtUtil;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
    }

    // Forgot password: Generate reset token and send email
    @Override
    public void forgotPassword(String emailOrMobile) throws Exception {
        Optional<User> userOptional;
        if (emailOrMobile.contains("@")) {
            userOptional = userRepository.findByEmail(emailOrMobile);
        } else {
            userOptional = userRepository.findByMobileNumber(emailOrMobile);
        }

        if (userOptional.isEmpty()) {
            throw new Exception("User not found");
        }

        User user = userOptional.get();
        String token = UUID.randomUUID().toString();  // Generate a random token
        LocalDateTime expirationDate = LocalDateTime.now().plusHours(1);  // Token expires in 1 hour

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setExpirationDate(expirationDate);
        resetToken.setUser(user);
        tokenRepository.save(resetToken);

        // Send reset token via email (you can replace this with SMS or another method)
        sendPasswordResetEmail(user.getEmail(), token);
    }

    private void sendPasswordResetEmail(String email, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Password Reset Request");
        message.setText("To reset your password, click the following link: " +
                "http://localhost:8080/api/auth/reset-password?token=" + token);
        mailSender.send(message);
    }

    // Reset password: Validate token and change the password
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

    @Override
    public void changePassword(String userId, String oldPassword, String newPassword) throws Exception {
        // Fetch user from the database
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception("User not found"));

        // Verify the old password
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new Exception("Old password is incorrect");
        }

        // Update password with new password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}

