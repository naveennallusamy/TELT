package com.telt.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    private final String SECRET_KEY = "VHJhbnNmb3JtaW5nRW50cnlMZXZlbFRhbGVudA==";  // Use a secure key

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractTenantId(String token) {
        return extractClaim(token, claims -> claims.get("tenantId", Long.class));  // Extract tenant ID from token
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .setSigningKey(SECRET_KEY)
                .parseClaimsJws(token)
                .getBody();
    }

    public String generateToken(String username, String role, Long tenantId) {
        Claims claims = Jwts.claims();
        claims.put("sub", username);
        claims.put("iat", new Date());
        if (!"SUPER_ADMIN".equals(role)) {
            claims.put("tenantId", tenantId);  // Only add tenantId if not super admin
        }
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60)) // 1 hour expiration
                .signWith(Keys.hmacShaKeyFor(SECRET_KEY.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, String username) {
        return (username.equals(extractUsername(token)) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
}

