package com.telt.support;

import com.telt.service.notification.PasswordResetNotifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records the last reset link per e-mail instead of sending mail.
 */
public class CapturingPasswordResetNotifier implements PasswordResetNotifier {

    private final Map<String, String> lastLinkByEmail = new ConcurrentHashMap<>();

    @Override
    public void sendPasswordResetLink(String email, String resetLink) {
        lastLinkByEmail.put(email, resetLink);
    }

    public String lastTokenFor(String email) {
        String link = lastLinkByEmail.get(email);
        return link == null ? null : link.substring(link.indexOf("token=") + "token=".length());
    }
}
