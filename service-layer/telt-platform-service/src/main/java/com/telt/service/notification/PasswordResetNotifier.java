package com.telt.service.notification;

public interface PasswordResetNotifier {

    void sendPasswordResetLink(String email, String resetLink);
}
