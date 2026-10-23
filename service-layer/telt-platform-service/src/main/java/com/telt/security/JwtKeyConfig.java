package com.telt.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.telt.config.properties.JwtProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RS256 signing keys. In production supply PEM files via {@code JWT_PRIVATE_KEY_LOCATION} /
 * {@code JWT_PUBLIC_KEY_LOCATION}; otherwise an ephemeral key pair is generated (tokens then do not survive a
 * restart and are not valid across replicas).
 */
@Slf4j
@Configuration
public class JwtKeyConfig {

    @Bean
    public KeyPair jwtSigningKeyPair(JwtProperties properties, ResourceLoader resourceLoader)
            throws GeneralSecurityException, IOException {
        if (isBlank(properties.privateKeyLocation()) || isBlank(properties.publicKeyLocation())) {
            log.warn("JWT key locations not configured; generating an ephemeral RSA key pair (development only)");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = factory.generatePublic(
                new X509EncodedKeySpec(readPem(resourceLoader, properties.publicKeyLocation())));
        PrivateKey privateKey = factory.generatePrivate(
                new PKCS8EncodedKeySpec(readPem(resourceLoader, properties.privateKeyLocation())));
        return new KeyPair(publicKey, privateKey);
    }

    @Bean
    public JwtEncoder jwtEncoder(KeyPair jwtSigningKeyPair) {
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) jwtSigningKeyPair.getPublic())
                .privateKey((RSAPrivateKey) jwtSigningKeyPair.getPrivate())
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    @Bean
    public JwtDecoder jwtDecoder(KeyPair jwtSigningKeyPair, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) jwtSigningKeyPair.getPublic()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer())));
        return decoder;
    }

    private static byte[] readPem(ResourceLoader loader, String location) throws IOException, InvalidKeySpecException {
        try (InputStream in = loader.getResource(location).getInputStream()) {
            String pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
            String base64 = pem.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
            if (base64.isEmpty()) {
                throw new InvalidKeySpecException("Empty PEM at " + location);
            }
            return Base64.getDecoder().decode(base64);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
