package com.example.devflowbackend.model;

import java.time.Instant;

public record UserEntity(long id, String username, String passwordHash, Instant createdAt) {
}
