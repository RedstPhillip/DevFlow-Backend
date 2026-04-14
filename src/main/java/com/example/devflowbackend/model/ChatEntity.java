package com.example.devflowbackend.model;

import java.time.Instant;

public record ChatEntity(long id, ChatType type, Instant createdAt) {
}
