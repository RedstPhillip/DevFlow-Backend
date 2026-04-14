package com.example.devflowbackend.auth.dto;

import java.time.Instant;

public record TokenRefreshResponse(String accessToken, String refreshToken, Instant expiresAt) {
}
