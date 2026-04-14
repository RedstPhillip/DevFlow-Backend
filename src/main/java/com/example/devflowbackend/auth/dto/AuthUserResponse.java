package com.example.devflowbackend.auth.dto;

import java.time.Instant;

public record AuthUserResponse(long id, String username, Instant createdAt) {
}
