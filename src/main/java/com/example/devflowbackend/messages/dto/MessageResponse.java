package com.example.devflowbackend.messages.dto;

import java.time.Instant;

public record MessageResponse(long id, long chatId, long transmitterId, String content, Instant createdAt) {
}
