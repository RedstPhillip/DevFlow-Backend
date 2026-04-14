package com.example.devflowbackend.auth.dto;

import java.time.Instant;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        Instant expiresAt,
        AuthUserResponse user
) {
}
