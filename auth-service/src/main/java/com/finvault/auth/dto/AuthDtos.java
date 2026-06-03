package com.finvault.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// ---- Requests ----

public class AuthDtos {

    public record RegisterRequest(
            @NotBlank @Email
            String email,

            @NotBlank @Size(min = 8, message = "Password must be at least 8 characters")
            String password,

            @NotBlank
            String firstName,

            @NotBlank
            String lastName
    ) {}

    public record LoginRequest(
            @NotBlank @Email
            String email,

            @NotBlank
            String password
    ) {}

    public record RefreshTokenRequest(
            @NotBlank
            String refreshToken
    ) {}

    // ---- Responses ----

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserInfo user
    ) {
        public static AuthResponse of(String access, String refresh, long expiresIn, UserInfo user) {
            return new AuthResponse(access, refresh, "Bearer", expiresIn, user);
        }
    }

    public record UserInfo(
            String id,
            String email,
            String firstName,
            String lastName,
            String role
    ) {}

    public record MessageResponse(String message) {}
}
