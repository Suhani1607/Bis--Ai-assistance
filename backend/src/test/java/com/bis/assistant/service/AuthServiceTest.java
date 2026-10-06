package com.bis.assistant.service;

import com.bis.assistant.dto.AuthDtos.*;
import com.bis.assistant.exception.ConflictException;
import com.bis.assistant.model.User;
import com.bis.assistant.repository.RefreshTokenRepository;
import com.bis.assistant.repository.UserRepository;
import com.bis.assistant.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepo;
    @Mock RefreshTokenRepository refreshTokenRepo;
    @Mock JwtService jwtService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authManager;

    @InjectMocks AuthService authService;

    @BeforeEach
    void injectExpiry() throws Exception {
        // Set private field refreshTokenExpirySecs = 2592000
        var field = AuthService.class.getDeclaredField("refreshTokenExpirySecs");
        field.setAccessible(true);
        field.set(authService, 2592000L);
    }

    @Test
    void register_success_returnsTokens() {
        when(userRepo.existsByEmail("new@bis.in")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$hashed");
        when(userRepo.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            // Simulate DB-assigned ID
            return User.builder()
                .email(u.getEmail()).name(u.getName())
                .passwordHash(u.getPasswordHash())
                .role(User.Role.USER).active(true).preferredLang("en")
                .build();
        });
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access.token.here");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh.token.here");
        when(jwtService.getAccessTokenExpirySeconds()).thenReturn(3600L);
        when(refreshTokenRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse resp = authService.register(
            new RegisterRequest("new@bis.in", "Test User", "password123", "en"));

        assertThat(resp.accessToken()).isEqualTo("access.token.here");
        assertThat(resp.user().email()).isEqualTo("new@bis.in");
        verify(userRepo).save(any());
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(userRepo.existsByEmail("dup@bis.in")).thenReturn(true);
        assertThatThrownBy(() -> authService.register(
            new RegisterRequest("dup@bis.in", "User", "pass1234", "en")))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already registered");
    }

    @Test
    void login_invalidCredentials_throwsUnauthorized() {
        doThrow(new org.springframework.security.authentication.BadCredentialsException("bad"))
            .when(authManager).authenticate(any());
        assertThatThrownBy(() -> authService.login(new LoginRequest("x@y.com", "wrong")))
            .isInstanceOf(RuntimeException.class);
    }
}
