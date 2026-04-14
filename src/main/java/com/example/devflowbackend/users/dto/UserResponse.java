package com.example.devflowbackend.users.dto;

import java.time.Instant;

public record UserResponse(long id, String username, Instant createdAt) {
}
