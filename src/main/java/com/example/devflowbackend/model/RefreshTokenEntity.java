package com.example.devflowbackend.model;

import java.time.Instant;

public record RefreshTokenEntity(
        long id,
        long userId,
        String tokenHash,
        Instant expiresAt,
        Instant createdAt,
        boolean revoked
) {
}
