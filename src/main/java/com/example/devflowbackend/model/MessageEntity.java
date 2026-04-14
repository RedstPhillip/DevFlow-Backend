package com.example.devflowbackend.model;

import java.time.Instant;

public record MessageEntity(long id, long chatId, long transmitterId, String content, Instant createdAt) {
}
