package com.bis.assistant.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
@Slf4j
public class JwtService {

    private final SecretKey secretKey;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry:3600}") long accessExpiry,
            @Value("${jwt.refresh-token-expiry:2592000}") long refreshExpiry) {

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be configured and non-empty. Startup aborted.");
        }
        if (secret.contains("change_me") || (secret.contains("secret") && secret.length() < 32)) {
            throw new IllegalStateException("JWT_SECRET is using an insecure or default placeholder value. Startup aborted.");
        }
        byte[] keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 256 bits (32 bytes). Current length is " + keyBytes.length + " bytes.");
        }

        this.secretKey          = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpiry  = accessExpiry  * 1000L;
        this.refreshTokenExpiry = refreshExpiry * 1000L;
    }

    // ── Generation ────────────────────────────────────────────

    public String generateAccessToken(UserDetails user, Map<String, Object> extraClaims) {
        return buildToken(user.getUsername(), extraClaims, accessTokenExpiry);
    }

    public String generateRefreshToken(UserDetails user) {
        return buildToken(user.getUsername(), Map.of("type", "refresh"), refreshTokenExpiry);
    }

    private String buildToken(String subject, Map<String, Object> claims, long expiry) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .claims(claims)
            .subject(subject)
            .id(UUID.randomUUID().toString())
            .issuedAt(new Date(now))
            .expiration(new Date(now + expiry))
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact();
    }

    // ── Validation ────────────────────────────────────────────

    public boolean isValid(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isExpired(token);
        } catch (JwtException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    public boolean isExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    // ── Extraction ────────────────────────────────────────────

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public long getAccessTokenExpirySeconds() {
        return accessTokenExpiry / 1000;
    }
}
