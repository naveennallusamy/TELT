package com.telt.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("telt.bootstrap.super-admin")
public record BootstrapAdminProperties(String username, String email, String mobileNumber, String password) {
}
