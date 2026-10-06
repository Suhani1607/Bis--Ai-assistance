package com.bis.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.UUID;

public class AuthDtos {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 2, max = 100) String name,
        @NotBlank @Size(min = 8) String password,
        String preferredLang
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefreshRequest(
        @NotBlank String refreshToken
    ) {}

    @Builder
    public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserDto user
    ) {}

    @Builder
    public record UserDto(
        UUID id,
        String email,
        String name,
        String role,
        String preferredLang
    ) {}
}
