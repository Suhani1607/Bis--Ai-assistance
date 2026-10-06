package com.bis.assistant.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setup() {
        // 64-char secret (512 bits) — well above HS256 minimum
        String secret = "test-secret-key-that-is-definitely-long-enough-for-hmac-sha256-ok";
        jwtService = new JwtService(secret, 3600, 86400);
    }

    @Test
    void generateAndValidateAccessToken() {
        UserDetails user = makeUser("test@bis.in");
        String token = jwtService.generateAccessToken(user, Map.of("role", "USER"));

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("test@bis.in");
        assertThat(jwtService.isValid(token, user)).isTrue();
        assertThat(jwtService.isExpired(token)).isFalse();
    }

    @Test
    void generateRefreshToken_isValid() {
        UserDetails user = makeUser("admin@bis.in");
        String token = jwtService.generateRefreshToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("admin@bis.in");
        assertThat(jwtService.isExpired(token)).isFalse();
    }

    @Test
    void isValid_wrongUser_returnsFalse() {
        UserDetails user1 = makeUser("user1@bis.in");
        UserDetails user2 = makeUser("user2@bis.in");
        String token = jwtService.generateAccessToken(user1, Map.of());

        assertThat(jwtService.isValid(token, user2)).isFalse();
    }

    @Test
    void extractJti_isUnique() {
        UserDetails user = makeUser("test@bis.in");
        String token1 = jwtService.generateAccessToken(user, Map.of());
        String token2 = jwtService.generateAccessToken(user, Map.of());

        assertThat(jwtService.extractJti(token1))
            .isNotEqualTo(jwtService.extractJti(token2));
    }

    @Test
    void getAccessTokenExpirySeconds_returnsConfiguredValue() {
        assertThat(jwtService.getAccessTokenExpirySeconds()).isEqualTo(3600L);
    }

    private UserDetails makeUser(String email) {
        return User.withUsername(email)
            .password("$2a$12$dummy")
            .authorities(Collections.emptyList())
            .build();
    }
}
