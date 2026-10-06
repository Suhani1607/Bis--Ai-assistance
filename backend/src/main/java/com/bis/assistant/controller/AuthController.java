package com.bis.assistant.controller;

import com.bis.assistant.dto.AuthDtos.*;
import com.bis.assistant.exception.UnauthorizedException;
import com.bis.assistant.security.UserPrincipal;
import com.bis.assistant.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest req,
            HttpServletRequest request) {
        AuthResponse resp = authService.register(req);
        ResponseCookie cookie = createRefreshCookie(resp.refreshToken(), request.isSecure());
        return ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(resp);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest request) {
        AuthResponse resp = authService.login(req);
        ResponseCookie cookie = createRefreshCookie(resp.refreshToken(), request.isSecure());
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(resp);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestBody(required = false) RefreshRequest req,
            @CookieValue(name = "refresh_token", required = false) String cookieToken,
            HttpServletRequest request) {
        String token = (req != null && req.refreshToken() != null && !req.refreshToken().isBlank())
            ? req.refreshToken() : cookieToken;

        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Refresh token is missing");
        }

        AuthResponse resp = authService.refresh(new RefreshRequest(token));
        ResponseCookie cookie = createRefreshCookie(resp.refreshToken(), request.isSecure());
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(resp);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) RefreshRequest req,
            @CookieValue(name = "refresh_token", required = false) String cookieToken) {
        String token = (req != null && req.refreshToken() != null && !req.refreshToken().isBlank())
            ? req.refreshToken() : cookieToken;

        if (token != null && !token.isBlank()) {
            authService.logout(token);
        }
        ResponseCookie clearCookie = ResponseCookie.from("refresh_token", "")
            .httpOnly(true)
            .path("/")
            .maxAge(0)
            .sameSite("Lax")
            .build();
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
            .build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(authService.getProfile(principal.id()));
    }

    private ResponseCookie createRefreshCookie(String refreshToken, boolean isSecure) {
        return ResponseCookie.from("refresh_token", refreshToken)
            .httpOnly(true)
            .secure(isSecure)
            .path("/")
            .maxAge(Duration.ofDays(30))
            .sameSite("Lax")
            .build();
    }
}
