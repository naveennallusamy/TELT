package com.telt.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("telt.security.jwt")
public record JwtProperties(String issuer, Duration accessTokenTtl, Duration refreshTokenTtl,
                            String privateKeyLocation, String publicKeyLocation) {
}
