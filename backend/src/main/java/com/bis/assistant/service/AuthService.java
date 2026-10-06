package com.bis.assistant.service;

import com.bis.assistant.dto.AuthDtos.*;
import com.bis.assistant.exception.ConflictException;
import com.bis.assistant.exception.ResourceNotFoundException;
import com.bis.assistant.exception.UnauthorizedException;
import com.bis.assistant.model.RefreshToken;
import com.bis.assistant.model.User;
import com.bis.assistant.repository.RefreshTokenRepository;
import com.bis.assistant.repository.UserRepository;
import com.bis.assistant.security.JwtService;
import com.bis.assistant.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepo;
    private final RefreshTokenRepository refreshTokenRepo;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;

    @Value("${jwt.refresh-token-expiry:2592000}")
    private long refreshTokenExpirySecs;

    // ── Register ──────────────────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.email())) {
            throw new ConflictException("Email already registered");
        }

        User user = User.builder()
            .email(req.email().toLowerCase().strip())
            .name(req.name().strip())
            .passwordHash(passwordEncoder.encode(req.password()))
            .role(User.Role.USER)
            .preferredLang(req.preferredLang() != null ? req.preferredLang() : "en")
            .emailVerified(false)
            .active(true)
            .build();

        user = userRepo.save(user);
        log.info("New user registered: {}", user.getEmail());
        return issueTokens(user);
    }

    // ── Login ─────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest req) {
        try {
            authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email().toLowerCase(), req.password())
            );
        } catch (BadCredentialsException e) {
            throw new UnauthorizedException("Invalid email or password");
        }

        User user = userRepo.findByEmail(req.email().toLowerCase())
            .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!user.isActive()) throw new UnauthorizedException("Account is disabled");

        return issueTokens(user);
    }

    // ── Refresh ───────────────────────────────────────────────

    @Transactional
    public AuthResponse refresh(RefreshRequest req) {
        String hash = sha256(req.refreshToken());
        RefreshToken stored = refreshTokenRepo.findByTokenHash(hash)
            .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new UnauthorizedException("Refresh token expired or revoked");
        }

        // Rotate: revoke old, issue new pair
        stored.setRevoked(true);

        User user = userRepo.findById(stored.getUserId())
            .orElseThrow(() -> new UnauthorizedException("User not found"));

        return issueTokens(user);
    }

    // ── Logout ────────────────────────────────────────────────

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        refreshTokenRepo.findByTokenHash(hash).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepo.save(t);
        });
    }

    // ── Profile ───────────────────────────────────────────────

    public UserDto getProfile(UUID userId) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toDto(user);
    }

    // ── Helpers ───────────────────────────────────────────────

    private AuthResponse issueTokens(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        Map<String, Object> claims = Map.of(
            "role", user.getRole().name(),
            "userId", user.getId().toString()
        );

        String accessToken  = jwtService.generateAccessToken(principal, claims);
        String refreshToken = jwtService.generateRefreshToken(principal);

        // Persist hashed refresh token
        RefreshToken rt = RefreshToken.builder()
            .userId(user.getId())
            .tokenHash(sha256(refreshToken))
            .expiresAt(OffsetDateTime.now().plusSeconds(refreshTokenExpirySecs))
            .revoked(false)
            .build();
        refreshTokenRepo.save(rt);

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(jwtService.getAccessTokenExpirySeconds())
            .user(toDto(user))
            .build();
    }

    private UserDto toDto(User u) {
        return UserDto.builder()
            .id(u.getId())
            .email(u.getEmail())
            .name(u.getName())
            .role(u.getRole().name())
            .preferredLang(u.getPreferredLang())
            .build();
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
