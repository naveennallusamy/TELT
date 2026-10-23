package com.telt.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("telt.account")
public record AccountProperties(String passwordResetUrl, Duration passwordResetTtl, int maxFailedLogins,
                                Duration lockoutDuration, String mailFrom) {
}
